package dev.proxboss.aoe.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoe.AoEApplication
import dev.proxboss.aoe.data.StatusFrame
import dev.proxboss.aoe.data.StatusFrameParser
import dev.proxboss.aoe.model.GroupInfo
import dev.proxboss.aoe.model.SearchHit
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.model.SessionFilter
import dev.proxboss.aoe.model.SystemHealth
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
import java.time.Instant
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class SessionSortMode(val labelFr: String) {
    ACTIVITY("Activité"),
    MANUAL("Ordre manuel"),
}

data class WorkspaceUi(
    val id: String,
    val label: String,
    val projectPath: String,
    val sessions: List<Session>,
    val scratch: Boolean = false,
    val collapsed: Boolean = false,
)

data class SessionGroupUi(
    val path: String,
    val label: String,
    val sessions: List<Session>,
    val collapsed: Boolean = false,
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
    val groupsInfo: List<GroupInfo> = emptyList(),
    val collapsedGroups: Set<String> = emptySet(),
    val sortMode: SessionSortMode = SessionSortMode.ACTIVITY,
    val activeCount: Int = 0,
    val waitingCount: Int = 0,
    val unreadCount: Int = 0,
    val workspaceOrdering: List<String> = emptyList(),
    val workspaces: List<WorkspaceUi> = emptyList(),
    val undoTrashSession: Session? = null,
    val health: SystemHealth? = null,
)

class SessionsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AoEApplication).container
    private val store = container.sessionStore
    private val repository = container.repository
    private val settings = container.settings

    private val _ui = MutableStateFlow(SessionsUiState())
    val ui: StateFlow<SessionsUiState> = _ui.asStateFlow()

    private var statusJob: Job? = null
    private var pollJob: Job? = null
    private var searchJob: Job? = null
    private var socket: WebSocket? = null
    private var reconnectAttempt = 0
    private var refreshInFlight = false

    init {
        viewModelScope.launch {
            store.sessions.collect { sessions ->
                _ui.value = recompute(_ui.value.copy(sessions = sessions))
            }
        }
        viewModelScope.launch {
            store.workspaceOrdering.collect { ordering ->
                _ui.value = recompute(_ui.value.copy(workspaceOrdering = ordering))
            }
        }
        viewModelScope.launch {
            settings.collapsedGroups.collect { collapsed ->
                _ui.value = recompute(_ui.value.copy(collapsedGroups = collapsed))
            }
        }
        viewModelScope.launch { refresh(showSpinner = true) }
        loadGroups()
        loadHealth()
        startStatusLoop()
        startPolling()
    }

    fun refresh(showSpinner: Boolean = false) {
        if (refreshInFlight) return
        refreshInFlight = true
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                refreshing = showSpinner,
                loading = if (showSpinner && _ui.value.sessions.isEmpty()) true else _ui.value.loading,
            )
            try {
                when (val result = repository.refresh()) {
                    is ApiResult.Ok -> {
                        _ui.value = _ui.value.copy(
                            refreshing = false,
                            loading = false,
                            offline = false,
                            error = null,
                        )
                        loadGroups()
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
            } finally {
                _ui.value = _ui.value.copy(refreshing = false)
                refreshInFlight = false
            }
        }
    }

    fun setFilter(filter: SessionFilter) {
        _ui.value = recompute(_ui.value.copy(filter = filter))
    }


    fun createGroup(path: String) {
        val trimmed = path.trim().trimEnd('/')
        if (trimmed.isBlank()) {
            _ui.value = _ui.value.copy(message = "Le chemin du groupe est vide")
            return
        }
        viewModelScope.launch {
            when (val result = repository.createGroup(trimmed)) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(message = "Groupe créé")
                    loadGroups()
                    refresh()
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(message = result.message)
            }
        }
    }

    fun renameGroup(oldPath: String, newPath: String) {
        val trimmed = newPath.trim().trimEnd('/')
        if (trimmed.isBlank()) {
            _ui.value = _ui.value.copy(message = "Le chemin du groupe est vide")
            return
        }
        viewModelScope.launch {
            when (val result = repository.renameGroup(oldPath, trimmed)) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(message = "Groupe renommé")
                    loadGroups()
                    refresh()
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(message = result.message)
            }
        }
    }

    fun deleteGroup(path: String, mode: String) {
        viewModelScope.launch {
            when (val result = repository.deleteGroup(path, mode)) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(message = "Groupe supprimé")
                    loadGroups()
                    refresh()
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(message = result.message)
            }
        }
    }

    fun moveSessionToGroup(session: Session, group: String) {
        optimistic(
            session,
            session.copy(groupPath = group),
            successMessage = if (group.isBlank()) "Session déplacée hors groupe" else "Session déplacée vers $group",
        ) { repository.moveSessionToGroup(session.id, group) }
    }

    fun moveWorkspace(index: Int, delta: Int) {
        val workspaces = _ui.value.workspaces
        val target = index + delta
        if (index !in workspaces.indices || target !in workspaces.indices) return
        val reordered = workspaces.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
        val previous = _ui.value.workspaceOrdering
        val generated = reordered.flatMap { workspace ->
            workspace.sessions.map(::workspaceIdFor)
        }.distinct()
        val order = generated + previous.filterNot { it in generated }
        _ui.value = recompute(_ui.value.copy(workspaceOrdering = order))
        viewModelScope.launch {
            when (val result = repository.updateWorkspaceOrdering(order)) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(message = "Ordre des workspaces enregistré")
                is ApiResult.Err -> {
                    store.setWorkspaceOrdering(previous)
                    _ui.value = recompute(
                        _ui.value.copy(workspaceOrdering = previous, message = result.message),
                    )
                }
            }
        }
    }

    fun toggleWorkspace(id: String) {
        if (id.isBlank()) return
        val key = "ws:$id"
        val collapsed = _ui.value.collapsedGroups
        val nowCollapsed = key !in collapsed
        val next = if (nowCollapsed) collapsed + key else collapsed - key
        _ui.value = recompute(_ui.value.copy(collapsedGroups = next))
        viewModelScope.launch { settings.setGroupCollapsed(key, nowCollapsed) }
    }

    fun toggleSortMode() {
        val next = if (_ui.value.sortMode == SessionSortMode.ACTIVITY) {
            SessionSortMode.MANUAL
        } else {
            SessionSortMode.ACTIVITY
        }
        _ui.value = recompute(
            _ui.value.copy(
                sortMode = next,
                message = "Tri : ${next.labelFr}",
            ),
        )
    }

    fun reorder(session: Session, up: Boolean) {
        val nextMode = SessionSortMode.MANUAL
        _ui.value = recompute(_ui.value.copy(sortMode = nextMode))
        viewModelScope.launch {
            when (
                val result = repository.reorderSession(
                    session.id,
                    session.groupPath,
                    session.profile.ifBlank { "main" },
                    up,
                )
            ) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(message = "Ordre mis à jour")
                    delay(300)
                    refresh()
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(message = result.message)
            }
        }
    }

    private fun loadHealth() {
        viewModelScope.launch {
            when (val result = repository.fetchHealth()) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(health = result.value)
                is ApiResult.Err -> Unit
            }
        }
    }

    private fun loadGroups() {
        viewModelScope.launch {
            when (val result = repository.fetchGroups()) {
                is ApiResult.Ok -> {
                    _ui.value = recompute(_ui.value.copy(groupsInfo = result.value))
                }
                is ApiResult.Err -> Unit
            }
        }
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
        val next = session.copy(pinnedAt = if (session.isPinned) null else Instant.now().toString())
        optimistic(session, next) { repository.setPinned(session.id, !session.isPinned) }
    }

    fun toggleFavorite(session: Session) {
        optimistic(session, session.copy(favorited = !session.favorited)) {
            repository.setFavorited(session.id, !session.favorited)
        }
    }

    fun toggleMuted(session: Session) {
        val muted = session.notifyOnWaiting == false &&
            session.notifyOnIdle == false &&
            session.notifyOnError == false
        val next = session.copy(
            notifyOnWaiting = !muted,
            notifyOnIdle = !muted,
            notifyOnError = !muted,
        )
        optimistic(session, next) { repository.setMuted(session.id, !muted) }
    }

    fun toggleRead(session: Session) {
        optimistic(session, session.copy(unread = !session.unread)) {
            repository.setUnread(session.id, !session.unread)
        }
    }

    fun rename(session: Session, title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) {
            _ui.value = _ui.value.copy(message = "Le titre ne peut pas être vide")
            return
        }
        optimistic(
            session,
            session.copy(title = trimmed),
            successMessage = "Session renommée",
        ) { repository.rename(session.id, trimmed) }
    }

    fun toggleArchive(session: Session) {
        val archived = !session.isArchived
        val next = session.copy(archivedAt = if (archived) Instant.now().toString() else null)
        optimistic(
            session,
            next,
            successMessage = if (archived) "Session archivée" else "Session désarchivée",
            refreshAfter = true,
        ) { repository.setArchived(session.id, archived) }
    }

    fun snooze(session: Session, minutes: Int?) {
        val next = session.copy(
            snoozedUntil = minutes?.let { Instant.now().plusSeconds(it * 60L).toString() },
        )
        optimistic(
            session,
            next,
            successMessage = if (minutes == null) "Session réveillée" else "Session mise en pause",
        ) { repository.setSnoozed(session.id, minutes) }
    }

    fun setColor(session: Session, color: String?) {
        optimistic(
            session,
            session.copy(color = color),
            successMessage = if (color == null) "Couleur effacée" else "Couleur appliquée",
        ) { repository.setColor(session.id, color) }
    }

    fun trash(session: Session) {
        val previous = store.session(session.id) ?: session
        store.upsert(previous.copy(trashedAt = Instant.now().toString()))
        _ui.value = _ui.value.copy(
            undoTrashSession = previous,
            message = "Session déplacée dans la corbeille",
        )
        viewModelScope.launch {
            when (val result = repository.trash(session.id)) {
                is ApiResult.Ok -> {
                    delay(700)
                    refresh()
                }
                is ApiResult.Err -> {
                    store.upsert(previous)
                    _ui.value = _ui.value.copy(undoTrashSession = null, message = result.message)
                }
            }
        }
    }

    fun undoTrash() {
        val previous = _ui.value.undoTrashSession ?: return
        _ui.value = _ui.value.copy(undoTrashSession = null)
        restore(previous)
    }

    fun dismissUndo() {
        _ui.value = _ui.value.copy(undoTrashSession = null)
    }

    fun restore(session: Session) {
        optimistic(
            session,
            session.copy(trashedAt = null),
            successMessage = "Session restaurée",
            refreshAfter = true,
        ) { repository.restore(session.id) }
    }

    fun stop(session: Session) {
        optimistic(
            session,
            session.copy(status = dev.proxboss.aoe.model.SessionStatus.STOPPED),
            successMessage = "Arrêt demandé",
            refreshAfter = true,
        ) { repository.stop(session.id) }
    }

    fun start(session: Session) {
        optimistic(
            session,
            session.copy(status = dev.proxboss.aoe.model.SessionStatus.STARTING),
            successMessage = "Démarrage demandé",
            refreshAfter = true,
        ) { repository.start(session.id) }
    }

    fun restart(session: Session) {
        optimistic(
            session,
            session.copy(status = dev.proxboss.aoe.model.SessionStatus.STARTING),
            successMessage = "Redémarrage demandé",
            refreshAfter = true,
        ) { repository.restart(session.id) }
    }

    fun markRead(session: Session) {
        if (!session.unread) return
        store.updateSession(session.id) { it.copy(unread = false) }
        viewModelScope.launch { repository.setUnread(session.id, false) }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null, error = null)
    }

    fun onResume() {
        refresh()
        if (!_ui.value.live && socket == null) {
            startStatusLoop()
        }
    }

    private fun optimistic(
        previous: Session,
        next: Session,
        successMessage: String? = null,
        refreshAfter: Boolean = false,
        action: suspend () -> ApiResult<Unit>,
    ) {
        store.upsert(next)
        viewModelScope.launch {
            when (val result = action()) {
                is ApiResult.Ok -> {
                    if (successMessage != null) {
                        _ui.value = _ui.value.copy(message = successMessage)
                    }
                    if (refreshAfter) {
                        delay(600)
                        refresh()
                    }
                }
                is ApiResult.Err -> {
                    store.upsert(previous)
                    _ui.value = _ui.value.copy(message = result.message)
                }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(if (_ui.value.live) 30_000 else 15_000)
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
                val delayMs = RECONNECT_DELAYS[reconnectAttempt.coerceAtMost(RECONNECT_DELAYS.lastIndex)]
                reconnectAttempt += 1
                delay(delayMs)
            }
        }
    }

    private inner class StatusListener(private val signal: Channel<Unit>) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempt = 0
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

        val byGroup = filtered.groupBy { it.groupPath }.toMutableMap()
        val showEmptyGroups = query.isEmpty() &&
            (state.filter == SessionFilter.ACTIVE || state.filter == SessionFilter.ALL)
        if (showEmptyGroups) {
            state.groupsInfo.forEach { info ->
                if (info.path !in byGroup) byGroup[info.path] = emptyList()
            }
        }
        if (byGroup.isEmpty() && filtered.isNotEmpty()) {
            byGroup[""] = filtered
        }

        val groupOrder = state.groupsInfo.mapIndexed { index, info -> info.path to index }.toMap()
        val position = state.sessions.mapIndexed { index, session -> session.id to index }.toMap()
        val groups = byGroup.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<Session>>> { it.key.isEmpty() }
                    .thenBy { groupOrder[it.key] ?: Int.MAX_VALUE }
                    .thenBy { it.key },
            )
            .map { (path, sessions) ->
                val ordered = if (state.sortMode == SessionSortMode.MANUAL) {
                    sessions.sortedWith(
                        compareByDescending<Session> { it.isPinned }
                            .thenBy { position[it.id] ?: Int.MAX_VALUE },
                    )
                } else {
                    sessions.sortedWith(
                        compareByDescending<Session> { it.isPinned }
                            .thenByDescending { it.favorited }
                            .thenByDescending { it.activityAt?.toEpochMilli() ?: 0L }
                            .thenBy { it.title.lowercase() },
                    )
                }
                SessionGroupUi(
                    path = path,
                    label = if (path.isBlank()) "Sans groupe" else path,
                    sessions = ordered,
                    collapsed = path in state.collapsedGroups,
                )
            }

        val visible = state.sessions.filterNot { it.isTrashed }
        val rank = state.workspaceOrdering.withIndex().associate { it.value to it.index }
        val workspaceDrafts = filtered
            .groupBy {
                if (it.scratch) "__scratch__" else normalizeProjectPath(it.mainRepoPath ?: it.projectPath)
            }
            .map { (projectPath, sessions) ->
                val ordered = if (state.sortMode == SessionSortMode.MANUAL) {
                    sessions.sortedWith(
                        compareByDescending<Session> { it.isPinned }
                            .thenBy { position[it.id] ?: Int.MAX_VALUE },
                    )
                } else {
                    sessions.sortedWith(
                        compareByDescending<Session> { it.isPinned }
                            .thenByDescending { it.favorited }
                            .thenByDescending { it.activityAt?.toEpochMilli() ?: 0L }
                            .thenBy { it.title.lowercase() },
                    )
                }
                val bestRank = sessions.minOfOrNull { rank[workspaceIdFor(it)] ?: Int.MAX_VALUE }
                    ?: Int.MAX_VALUE
                val scratch = projectPath == "__scratch__"
                WorkspaceDraft(
                    id = projectPath,
                    label = if (scratch) "Scratch" else projectPath.substringAfterLast('/').ifBlank { projectPath },
                    projectPath = projectPath,
                    scratch = scratch,
                    sessions = ordered,
                    rank = bestRank,
                    pinned = sessions.any { it.isPinned },
                    latest = sessions.maxOfOrNull { it.activityAt?.toEpochMilli() ?: 0L } ?: 0L,
                    collapsed = "ws:$projectPath" in state.collapsedGroups,
                )
            }
            .sortedWith(
                compareByDescending<WorkspaceDraft> { it.pinned }
                    .thenBy { it.rank }
                    .thenByDescending { it.latest }
                    .thenBy { it.label.lowercase() },
            )
        val workspaces = workspaceDrafts.map {
            WorkspaceUi(
                id = it.id,
                label = it.label,
                projectPath = it.projectPath,
                sessions = it.sessions,
                scratch = it.scratch,
                collapsed = it.collapsed,
            )
        }
        return state.copy(
            groups = groups,
            titles = state.sessions.associate { it.id to it.title },
            workspaces = workspaces,
            activeCount = visible.count {
                !it.isArchived && (it.status == dev.proxboss.aoe.model.SessionStatus.RUNNING ||
                    it.status == dev.proxboss.aoe.model.SessionStatus.WAITING ||
                    it.status == dev.proxboss.aoe.model.SessionStatus.STARTING ||
                    it.status == dev.proxboss.aoe.model.SessionStatus.CREATING)
            },
            waitingCount = visible.count {
                !it.isArchived && it.status == dev.proxboss.aoe.model.SessionStatus.WAITING
            },
            unreadCount = visible.count { it.unread },
        )
    }

    private data class WorkspaceDraft(
        val id: String,
        val label: String,
        val projectPath: String,
        val sessions: List<Session>,
        val scratch: Boolean,
        val rank: Int,
        val pinned: Boolean,
        val latest: Long,
        val collapsed: Boolean,
    )

    private fun normalizeProjectPath(path: String): String {
        val trimmed = path.trimEnd('/')
        return trimmed.ifBlank { "/" }
    }

    private fun workspaceIdFor(session: Session): String {
        val repoPath = normalizeProjectPath(session.mainRepoPath ?: session.projectPath)
        return if (!session.branch.isNullOrBlank()) {
            "$repoPath::${session.branch}"
        } else {
            "$repoPath::__session__::${session.id}"
        }
    }

    companion object {
        private val RECONNECT_DELAYS = longArrayOf(2_000, 5_000, 10_000, 20_000, 30_000)
    }

    override fun onCleared() {
        socket?.close(1000, "screen closed")
        statusJob?.cancel()
        pollJob?.cancel()
        searchJob?.cancel()
        super.onCleared()
    }
}
