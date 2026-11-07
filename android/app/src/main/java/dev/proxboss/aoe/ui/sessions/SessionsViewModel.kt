package dev.proxboss.aoe.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoe.AoEApplication
import dev.proxboss.aoe.data.StatusFrame
import dev.proxboss.aoe.data.StatusFrameParser
import dev.proxboss.aoe.model.SearchHit
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.model.SessionFilter
import dev.proxboss.aoe.net.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

data class SessionGroupUi(
    val path: String,
    val label: String,
    val sessions: List<Session>,
)

data class SessionsUiState(
    val sessions: List<Session> = emptyList(),
    val groups: List<SessionGroupUi> = emptyList(),
    val searchHits: List<SearchHit> = emptyList(),
    val titles: Map<String, String> = emptyMap(),
    val filter: SessionFilter = SessionFilter.ACTIVE,
    val query: String = "",
    val refreshing: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val offline: Boolean = false,
    val live: Boolean = false,
    val message: String? = null,
)

class SessionsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AoEApplication).container
    private val store = container.sessionStore
    private val repository = container.repository

    private val _ui = MutableStateFlow(SessionsUiState())
    val ui: StateFlow<SessionsUiState> = _ui.asStateFlow()

    private var statusJob: Job? = null
    private var pollJob: Job? = null
    private var searchJob: Job? = null
    private var socket: WebSocket? = null

    init {
        viewModelScope.launch {
            store.sessions.collect { sessions ->
                _ui.value = recompute(_ui.value.copy(sessions = sessions))
            }
        }
        viewModelScope.launch { refresh(showSpinner = true) }
        startStatusLoop()
        startPolling()
    }

    fun refresh(showSpinner: Boolean = false) {
        if (_ui.value.refreshing) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                refreshing = true,
                loading = if (showSpinner && _ui.value.sessions.isEmpty()) true else _ui.value.loading,
            )
            when (val result = repository.refresh()) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(
                        refreshing = false,
                        loading = false,
                        offline = false,
                        error = null,
                    )
                }
                is ApiResult.Err -> {
                    _ui.value = _ui.value.copy(
                        refreshing = false,
                        loading = false,
                        offline = true,
                        error = result.message,
                    )
                }
            }
        }
    }

    fun setFilter(filter: SessionFilter) {
        _ui.value = recompute(_ui.value.copy(filter = filter))
    }

    fun setQuery(query: String) {
        _ui.value = recompute(_ui.value.copy(query = query))
        searchJob?.cancel()
        if (query.isBlank()) {
            _ui.value = _ui.value.copy(searchHits = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            when (val result = repository.search(query.trim())) {
                is ApiResult.Ok -> {
                    if (_ui.value.query == query) {
                        _ui.value = _ui.value.copy(searchHits = result.value)
                    }
                }
                is ApiResult.Err -> Unit
            }
        }
    }

    fun togglePin(session: Session) {
        viewModelScope.launch {
            handleMutation(repository.setPinned(session.id, !session.isPinned))
        }
    }

    fun toggleFavorite(session: Session) {
        viewModelScope.launch {
            handleMutation(repository.setFavorited(session.id, !session.favorited))
        }
    }

    fun toggleMuted(session: Session) {
        val muted = session.notifyOnWaiting == false &&
            session.notifyOnIdle == false &&
            session.notifyOnError == false
        viewModelScope.launch {
            handleMutation(repository.setMuted(session.id, !muted))
        }
    }

    fun toggleRead(session: Session) {
        viewModelScope.launch {
            handleMutation(repository.setUnread(session.id, !session.unread))
        }
    }

    fun markRead(session: Session) {
        if (!session.unread) return
        viewModelScope.launch { repository.setUnread(session.id, false) }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null, error = null)
    }

    private suspend fun handleMutation(result: ApiResult<Unit>) {
        when (result) {
            is ApiResult.Ok -> _ui.value = _ui.value.copy(message = null)
            is ApiResult.Err -> _ui.value = _ui.value.copy(message = result.message)
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(if (_ui.value.live) 20_000 else 5_000)
                refresh()
            }
        }
    }

    private fun startStatusLoop() {
        statusJob?.cancel()
        statusJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val signal = Channel<Unit>(Channel.CONFLATED)
                val ws = container.client.openStatusSocket(StatusListener(signal))
                if (ws == null) {
                    delay(5_000)
                    continue
                }
                socket = ws
                signal.receive()
                socket = null
                _ui.value = _ui.value.copy(live = false)
                delay(3_000)
            }
        }
    }

    private inner class StatusListener(private val signal: Channel<Unit>) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _ui.value = _ui.value.copy(live = true, offline = false)
            viewModelScope.launch { refresh() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (val frame = StatusFrameParser.parse(text)) {
                StatusFrame.Ready, StatusFrame.Resync -> {
                    viewModelScope.launch { refresh() }
                }
                is StatusFrame.Status -> {
                    store.applyStatus(frame.id, frame.status)
                    if (store.session(frame.id) == null) {
                        viewModelScope.launch { refresh() }
                    }
                }
                StatusFrame.Unknown -> Unit
                is StatusFrame.SoundDue -> Unit
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _ui.value = _ui.value.copy(live = false)
            signal.trySend(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _ui.value = _ui.value.copy(live = false)
            signal.trySend(Unit)
        }
    }

    private fun recompute(state: SessionsUiState): SessionsUiState {
        val query = state.query.trim()
        val filtered = state.sessions
            .filter { session ->
                when (state.filter) {
                    SessionFilter.ACTIVE -> !session.isArchived && !session.isTrashed
                    SessionFilter.FAVORITES -> session.favorited && !session.isTrashed
                    SessionFilter.ALL -> !session.isTrashed
                    SessionFilter.ARCHIVED -> session.isArchived && !session.isTrashed
                    SessionFilter.TRASHED -> session.isTrashed
                }
            }
            .filter { session ->
                query.isEmpty() ||
                    session.title.contains(query, ignoreCase = true) ||
                    session.displayAgent.contains(query, ignoreCase = true) ||
                    session.model.orEmpty().contains(query, ignoreCase = true) ||
                    session.projectPath.contains(query, ignoreCase = true) ||
                    session.groupPath.contains(query, ignoreCase = true)
            }
        val groups = filtered
            .groupBy { it.groupPath }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Session>>> { it.key.isEmpty() }.thenBy { it.key })
            .map { (path, sessions) ->
                SessionGroupUi(
                    path = path,
                    label = if (path.isBlank()) "Sans groupe" else path,
                    sessions = sessions.sortedWith(
                        compareByDescending<Session> { it.isPinned }
                            .thenByDescending { it.favorited }
                            .thenByDescending { it.activityAt?.toEpochMilli() ?: 0L }
                            .thenBy { it.title.lowercase() },
                    ),
                )
            }
        return state.copy(
            groups = groups,
            titles = state.sessions.associate { it.id to it.title },
        )
    }

    override fun onCleared() {
        socket?.close(1000, "screen closed")
        statusJob?.cancel()
        pollJob?.cancel()
        searchJob?.cancel()
        super.onCleared()
    }
}
