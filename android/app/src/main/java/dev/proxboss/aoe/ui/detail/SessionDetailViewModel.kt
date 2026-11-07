package dev.proxboss.aoe.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoe.AoEApplication
import dev.proxboss.aoe.data.AcpFrame
import dev.proxboss.aoe.data.AcpFrameParser
import dev.proxboss.aoe.data.TranscriptBuilder
import dev.proxboss.aoe.model.AcpAgent
import dev.proxboss.aoe.model.AcpControl
import dev.proxboss.aoe.model.Approval
import dev.proxboss.aoe.model.DisplayItem
import dev.proxboss.aoe.model.QueuedPrompt
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.model.TranscriptDisplay
import dev.proxboss.aoe.net.ApiResult
import dev.proxboss.aoe.util.Ansi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID

enum class DetailTransport {
    NONE,
    ACP,
    TERMINAL,
}

data class SessionDetailUiState(
    val session: Session? = null,
    val transport: DetailTransport = DetailTransport.NONE,
    val connecting: Boolean = true,
    val connected: Boolean = false,
    val rows: List<DisplayItem> = emptyList(),
    val control: AcpControl = AcpControl(),
    val terminalText: String = "",
    val error: String? = null,
    val notice: String? = null,
    val offline: Boolean = false,
    val sending: Boolean = false,
    val approvalAction: String? = null,
    val configAction: String? = null,
    val sessionAction: String? = null,
    val acpAgents: List<AcpAgent> = emptyList(),
    val acpAgentsLoading: Boolean = false,
    val switchingAgent: String? = null,
    val queuedPrompts: List<QueuedPrompt> = emptyList(),
    val queueLoading: Boolean = false,
    val imageUploading: Boolean = false,
)

class SessionDetailViewModel(
    application: Application,
    private val sessionId: String,
) : AndroidViewModel(application) {
    private val container = (application as AoEApplication).container
    private val store = container.sessionStore
    private val repository = container.repository
    private val builder = TranscriptBuilder()

    private val _ui = MutableStateFlow(SessionDetailUiState())
    val ui: StateFlow<SessionDetailUiState> = _ui.asStateFlow()

    private var socket: WebSocket? = null
    private var retryJob: Job? = null
    private var transportStarted = false
    private var intentionallyClosed = false
    private var acpFailed = false
    private var terminalFailed = false
    private var markedRead = false
    private var reconnectAttempt = 0

    init {
        viewModelScope.launch {
            store.sessions.collect { sessions ->
                val session = sessions.firstOrNull { it.id == sessionId }
                _ui.value = _ui.value.copy(session = session)
                if (!transportStarted && session != null) {
                    transportStarted = true
                    chooseInitialTransport(session)
                }
                if (!markedRead && session?.unread == true) {
                    markedRead = true
                    repository.setUnread(sessionId, false)
                }
            }
        }
        viewModelScope.launch {
            refreshSession()
            if (!transportStarted) {
                transportStarted = true
                chooseInitialTransport(store.session(sessionId))
            }
        }
        startPeriodicRefresh()
        loadAcpAgents()
        loadQueue()
    }

    fun retryConnection() {
        retryJob?.cancel()
        reconnectAttempt = 0
        acpFailed = false
        terminalFailed = false
        chooseInitialTransport(_ui.value.session ?: store.session(sessionId))
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun showError(message: String) {
        _ui.value = _ui.value.copy(error = message)
    }

    fun onResume() {
        viewModelScope.launch { refreshSession() }
        if (!_ui.value.connected && !_ui.value.connecting &&
            _ui.value.switchingAgent == null && _ui.value.sessionAction == null
        ) {
            retryConnection()
        }
    }

    fun dismissNotice() {
        _ui.value = _ui.value.copy(notice = null)
    }

    fun sendPrompt(text: String, onSent: () -> Unit = {}) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _ui.value.sending) return
        val optimisticId = "local-${UUID.randomUUID()}"
        builder.addOptimistic(optimisticId, trimmed)
        _ui.value = _ui.value.copy(sending = true, error = null)
        publishRows()
        viewModelScope.launch {
            val result = repository.sendPrompt(
                sessionId,
                trimmed,
                forceTerminal = _ui.value.transport == DetailTransport.TERMINAL,
            )
            when (result) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(
                        sending = false,
                        notice = if (result.value.queued) "Message mis en file d'attente par l'agent." else _ui.value.notice,
                    )
                    if (result.value.queued) {
                        builder.clearOptimistic()
                        publishRows()
                    }
                    onSent()
                    loadQueue()
                }
                is ApiResult.Err -> {
                    builder.clearOptimistic()
                    _ui.value = _ui.value.copy(sending = false, error = result.message)
                    publishRows()
                }
            }
        }
    }

    fun cancelTurn() {
        runSessionAction("Annulation demandée") { repository.cancelTurn(sessionId) }
        viewModelScope.launch {
            delay(800)
            loadQueue()
        }
    }

    fun loadQueue() {
        if (_ui.value.queueLoading) return
        _ui.value = _ui.value.copy(queueLoading = true)
        viewModelScope.launch {
            when (val result = repository.listQueue(sessionId)) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(queuedPrompts = result.value, queueLoading = false)
                is ApiResult.Err -> _ui.value = _ui.value.copy(queueLoading = false)
            }
        }
    }

    fun sendQueuedPrompt(prompt: QueuedPrompt) {
        val previous = _ui.value.queuedPrompts
        _ui.value = _ui.value.copy(queuedPrompts = previous.filter { it.id != prompt.id }, error = null)
        viewModelScope.launch {
            when (val result = repository.sendQueuedPrompt(sessionId, prompt)) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(
                        notice = if (result.value.queued) {
                            "Le prompt reste en file d'attente."
                        } else {
                            "Prompt envoyé."
                        },
                    )
                    loadQueue()
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(
                    queuedPrompts = previous,
                    error = result.message,
                )
            }
        }
    }

    fun editQueuedPrompt(prompt: QueuedPrompt, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val previous = _ui.value.queuedPrompts
        _ui.value = _ui.value.copy(
            queuedPrompts = previous.map { if (it.id == prompt.id) it.copy(text = trimmed) else it },
        )
        viewModelScope.launch {
            when (val result = repository.editQueuedPrompt(sessionId, prompt.id, trimmed)) {
                is ApiResult.Ok -> Unit
                is ApiResult.Err -> _ui.value = _ui.value.copy(queuedPrompts = previous, error = result.message)
            }
        }
    }

    fun deleteQueuedPrompt(prompt: QueuedPrompt) {
        val previous = _ui.value.queuedPrompts
        _ui.value = _ui.value.copy(queuedPrompts = previous.filter { it.id != prompt.id })
        viewModelScope.launch {
            when (val result = repository.removeQueuedPrompt(sessionId, prompt.id)) {
                is ApiResult.Ok -> Unit
                is ApiResult.Err -> _ui.value = _ui.value.copy(queuedPrompts = previous, error = result.message)
            }
        }
    }

    fun clearQueue() {
        val previous = _ui.value.queuedPrompts
        _ui.value = _ui.value.copy(queuedPrompts = emptyList())
        viewModelScope.launch {
            when (val result = repository.clearQueue(sessionId)) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(notice = "File d'attente vidée")
                is ApiResult.Err -> _ui.value = _ui.value.copy(queuedPrompts = previous, error = result.message)
            }
        }
    }

    fun uploadImage(mimeType: String, base64: String, onPath: (String) -> Unit) {
        if (_ui.value.imageUploading) return
        _ui.value = _ui.value.copy(imageUploading = true, error = null)
        viewModelScope.launch {
            when (val result = repository.pasteImage(sessionId, mimeType, base64)) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(imageUploading = false, notice = "Image collée dans la session.")
                    onPath(result.value)
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(imageUploading = false, error = result.message)
            }
        }
    }
    fun stopSession() = runSessionAction(
        successMessage = "Arrêt demandé",
        closeStream = true,
        reconnectAfter = false,
    ) { repository.stop(sessionId) }
    fun startSession() = runSessionAction(
        successMessage = "Démarrage demandé",
        closeStream = true,
        reconnectAfter = true,
    ) { repository.start(sessionId) }
    fun restartSession() = runSessionAction(
        successMessage = "Redémarrage demandé",
        closeStream = true,
        reconnectAfter = true,
    ) { repository.restart(sessionId) }

    fun resolveApproval(approval: Approval, decision: String, optionId: String? = null) {
        if (_ui.value.approvalAction != null) return
        _ui.value = _ui.value.copy(approvalAction = approval.nonce, error = null)
        viewModelScope.launch {
            when (val result = repository.resolveApproval(sessionId, approval, decision, optionId)) {
                is ApiResult.Ok -> {
                    val control = _ui.value.control
                    _ui.value = _ui.value.copy(
                        control = control.copy(
                            pendingApprovals = control.pendingApprovals.filter { it.nonce != approval.nonce },
                        ),
                        approvalAction = null,
                    )
                }
                is ApiResult.Err -> {
                    _ui.value = _ui.value.copy(approvalAction = null, error = result.message)
                }
            }
        }
    }

    fun setMode(value: String) {
        val control = _ui.value.control
        val modeOption = control.modeOption
        if (_ui.value.configAction != null) return
        val key = "mode=$value"
        _ui.value = _ui.value.copy(configAction = key, error = null)
        viewModelScope.launch {
            val result = if (modeOption != null) {
                repository.setConfigOption(sessionId, modeOption.id, value)
            } else {
                repository.setMode(sessionId, value)
            }
            when (result) {
                is ApiResult.Ok -> {
                    val updated = _ui.value.control
                    _ui.value = _ui.value.copy(
                        control = updated.copy(
                            currentModeId = value,
                            configOptions = updated.configOptions.map { option ->
                                if (option.id == modeOption?.id) option.copy(currentValue = value) else option
                            },
                        ),
                        configAction = null,
                    )
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(configAction = null, error = result.message)
            }
        }
    }

    fun loadAcpAgents() {
        if (_ui.value.acpAgentsLoading) return
        _ui.value = _ui.value.copy(acpAgentsLoading = true)
        viewModelScope.launch {
            when (val result = repository.fetchAcpAgents()) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(acpAgents = result.value, acpAgentsLoading = false)
                is ApiResult.Err -> _ui.value = _ui.value.copy(acpAgentsLoading = false)
            }
        }
    }

    fun switchAgent(target: String) {
        if (_ui.value.switchingAgent != null) return
        _ui.value = _ui.value.copy(switchingAgent = target, error = null)
        intentionallyClosed = true
        socket?.close(1000, "agent switch")
        socket = null
        _ui.value = _ui.value.copy(connected = false)
        viewModelScope.launch {
            when (val result = repository.switchAgent(sessionId, target)) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(
                        switchingAgent = null,
                        notice = "Agent changé vers $target.",
                    )
                    reconnectSoon(1_200)
                    refreshSession()
                }
                is ApiResult.Err -> {
                    _ui.value = _ui.value.copy(switchingAgent = null, error = result.message)
                    reconnectSoon(1_500)
                }
            }
        }
    }

    fun setConfigOption(configId: String, value: String) {
        val key = "$configId=$value"
        if (_ui.value.configAction != null) return
        _ui.value = _ui.value.copy(configAction = key, error = null)
        viewModelScope.launch {
            when (val result = repository.setConfigOption(sessionId, configId, value)) {
                is ApiResult.Ok -> {
                    val control = _ui.value.control
                    _ui.value = _ui.value.copy(
                        control = control.copy(
                            configOptions = control.configOptions.map { option ->
                                if (option.id == configId) option.copy(currentValue = value) else option
                            },
                        ),
                        configAction = null,
                    )
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(configAction = null, error = result.message)
            }
        }
    }

    fun toggleMuted() {
        val session = _ui.value.session ?: return
        val muted = session.notifyOnWaiting == false &&
            session.notifyOnIdle == false &&
            session.notifyOnError == false
        viewModelScope.launch {
            when (val result = repository.setMuted(sessionId, !muted)) {
                is ApiResult.Ok -> Unit
                is ApiResult.Err -> _ui.value = _ui.value.copy(error = result.message)
            }
        }
    }

    private fun runSessionAction(
        successMessage: String,
        closeStream: Boolean = false,
        reconnectAfter: Boolean = false,
        block: suspend () -> ApiResult<Unit>,
    ) {
        if (_ui.value.sessionAction != null) return
        if (closeStream) {
            intentionallyClosed = true
            socket?.close(1000, "session action")
            socket = null
            _ui.value = _ui.value.copy(connected = false)
        }
        _ui.value = _ui.value.copy(sessionAction = successMessage, error = null)
        viewModelScope.launch {
            when (val result = block()) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(sessionAction = null, notice = successMessage)
                    refreshSession()
                    if (reconnectAfter) reconnectSoon(1_200)
                }
                is ApiResult.Err -> {
                    _ui.value = _ui.value.copy(sessionAction = null, error = result.message)
                    if (closeStream) reconnectSoon(1_500)
                }
            }
        }
    }

    private suspend fun refreshSession() {
        when (val result = repository.refresh()) {
            is ApiResult.Ok -> {
                _ui.value = _ui.value.copy(offline = false)
            }
            is ApiResult.Err -> {
                _ui.value = _ui.value.copy(offline = true)
            }
        }
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10_000)
                refreshSession()
            }
        }
    }

    private fun chooseInitialTransport(session: Session?) {
        closeSocket()
        retryJob?.cancel()
        when {
            session?.view == "terminal" -> startTerminal()
            else -> startAcp()
        }
    }

    private fun closeSocket() {
        intentionallyClosed = true
        socket?.close(1000, "transport switching")
        socket = null
    }

    private fun startAcp() {
        closeSocket()
        intentionallyClosed = false
        _ui.value = _ui.value.copy(
            transport = DetailTransport.ACP,
            connecting = true,
            connected = false,
            error = null,
        )
        val ws = container.client.openAcpSocket(sessionId, since = 0, listener = AcpListener())
        if (ws == null) {
            acpFailed = true
            fallbackOrRetry("Connexion ACP impossible")
            return
        }
        socket = ws
    }

    private fun startTerminal() {
        closeSocket()
        intentionallyClosed = false
        _ui.value = _ui.value.copy(
            transport = DetailTransport.TERMINAL,
            connecting = true,
            connected = false,
            error = null,
        )
        val ws = container.client.openLiveSocket(sessionId, listener = TerminalListener())
        if (ws == null) {
            terminalFailed = true
            fallbackOrRetry("Terminal indisponible")
            return
        }
        socket = ws
    }

    private fun fallbackOrRetry(message: String) {
        val session = _ui.value.session ?: store.session(sessionId)
        _ui.value = _ui.value.copy(connected = false, connecting = false)
        when {
            _ui.value.transport == DetailTransport.ACP && session?.hasTerminal == true && !terminalFailed -> {
                _ui.value = _ui.value.copy(notice = "Transcript ACP indisponible — bascule sur le terminal.")
                startTerminal()
            }
            _ui.value.transport == DetailTransport.TERMINAL && session?.acpCapable == true && !acpFailed -> {
                _ui.value = _ui.value.copy(notice = "Terminal indisponible — bascule sur le transcript ACP.")
                startAcp()
            }
            else -> {
                _ui.value = _ui.value.copy(error = message)
                scheduleRetry()
            }
        }
    }

    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        val delayMs = (2_000L shl reconnectAttempt.coerceAtMost(4)).coerceAtMost(30_000)
        reconnectAttempt += 1
        reconnectSoon(delayMs)
    }

    private fun reconnectSoon(delayMs: Long) {
        retryJob?.cancel()
        acpFailed = false
        terminalFailed = false
        retryJob = viewModelScope.launch {
            delay(delayMs)
            chooseInitialTransport(_ui.value.session ?: store.session(sessionId))
        }
    }

    private inner class AcpListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (socket !== webSocket) return
            reconnectAttempt = 0
            _ui.value = _ui.value.copy(connecting = false, connected = true, error = null)
            loadQueue()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (socket !== webSocket) return
            val frame = AcpFrameParser.parse(text) ?: return
            viewModelScope.launch(Dispatchers.Main.immediate) { handleAcpFrame(frame) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket !== webSocket) return
            acpFailed = true
            viewModelScope.launch(Dispatchers.Main.immediate) {
                fallbackOrRetry("Connexion au transcript interrompue")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket !== webSocket || intentionallyClosed) return
            acpFailed = true
            viewModelScope.launch(Dispatchers.Main.immediate) {
                fallbackOrRetry(
                    if (code == 1001) "Serveur en cours d'arrêt" else "Transcript déconnecté",
                )
            }
        }
    }

    private fun handleAcpFrame(frame: AcpFrame) {
        when (frame) {
            AcpFrame.Heartbeat -> Unit
            AcpFrame.Lagged -> {
                _ui.value = _ui.value.copy(notice = "Flux ACP en retard, resynchronisation…")
            }
            is AcpFrame.ReducedState -> {
                val payload = frame.payload
                val control = payload.mergeInto(_ui.value.control)
                _ui.value = _ui.value.copy(control = control)
                val model = payload.model
                if (!model.isNullOrBlank() && model != store.session(sessionId)?.model) {
                    viewModelScope.launch { repository.recordModel(sessionId, model) }
                }
                publishRows()
            }
            is AcpFrame.TranscriptSnapshot -> {
                builder.snapshot(frame.rows)
                publishRows()
            }
            is AcpFrame.Delta -> {
                builder.apply(frame.delta)
                publishRows()
            }
        }
    }

    private inner class TerminalListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (socket !== webSocket) return
            reconnectAttempt = 0
            terminalFailed = false
            _ui.value = _ui.value.copy(connecting = false, connected = true, error = null)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (socket !== webSocket) return
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return
            if (root.optString("type") != "frame") return
            val content = root.optString("content")
            val clean = Ansi.tail(Ansi.strip(content), 400)
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _ui.value = _ui.value.copy(terminalText = clean)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket !== webSocket) return
            terminalFailed = true
            viewModelScope.launch(Dispatchers.Main.immediate) {
                fallbackOrRetry("Terminal indisponible")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket !== webSocket || intentionallyClosed) return
            terminalFailed = true
            viewModelScope.launch(Dispatchers.Main.immediate) {
                fallbackOrRetry(
                    when (code) {
                        1013 -> "Terminal indisponible (tmux non prêt)"
                        else -> "Terminal déconnecté"
                    },
                )
            }
        }
    }

    private fun publishRows() {
        _ui.value = _ui.value.copy(rows = TranscriptDisplay.build(builder.withOptimistic()))
    }

    override fun onCleared() {
        intentionallyClosed = true
        retryJob?.cancel()
        socket?.close(1000, "screen closed")
        socket = null
        super.onCleared()
    }

    class Factory(
        private val application: Application,
        private val sessionId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SessionDetailViewModel(application, sessionId) as T
        }
    }
}
