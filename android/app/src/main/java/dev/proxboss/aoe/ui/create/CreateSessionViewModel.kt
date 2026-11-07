package dev.proxboss.aoe.ui.create

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoe.AoEApplication
import dev.proxboss.aoe.model.AgentInfo
import dev.proxboss.aoe.model.BranchInfo
import dev.proxboss.aoe.model.ConfigOption
import dev.proxboss.aoe.model.CreateSessionRequest
import dev.proxboss.aoe.model.CreationProgress
import dev.proxboss.aoe.model.CreationTrustReview
import dev.proxboss.aoe.model.DirEntry
import dev.proxboss.aoe.model.RecentProject
import dev.proxboss.aoe.net.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class CreateUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val agents: List<AgentInfo> = emptyList(),
    val selectedAgent: String? = null,
    val optionCatalog: Map<String, List<ConfigOption>> = emptyMap(),
    val home: String = "",
    val recentProjects: List<RecentProject> = emptyList(),
    val path: String = "",
    val pathIsRepo: Boolean? = null,
    val branches: List<BranchInfo> = emptyList(),
    val title: String = "",
    val group: String = "",
    val model: String? = null,
    val effort: String? = null,
    val structured: Boolean = true,
    val yoloMode: Boolean = false,
    val worktree: Boolean = false,
    val createNewBranch: Boolean = true,
    val worktreeBranch: String = "",
    val baseBranch: String = "",
    val scratch: Boolean = false,
    val showFolderPicker: Boolean = false,
    val browsePath: String = "",
    val browseEntries: List<DirEntry> = emptyList(),
    val browseHasMore: Boolean = false,
    val browseShowHidden: Boolean = false,
    val browseLoading: Boolean = false,
    val browseError: String? = null,
    val creating: Boolean = false,
    val progress: CreationProgress? = null,
    val createError: String? = null,
    val trust: CreationTrustReview? = null,
    val trustApplying: Boolean = false,
    val createdSessionId: String? = null,
) {
    val selectedAgentInfo: AgentInfo? get() = agents.firstOrNull { it.name == selectedAgent }
    val modelOption: ConfigOption? get() = optionCatalog[selectedAgent].orEmpty().firstOrNull { it.isModel }
    val effortOption: ConfigOption? get() = optionCatalog[selectedAgent].orEmpty().firstOrNull { it.isEffort }
    val canCreate: Boolean
        get() = !creating && !loading && selectedAgentInfo?.installed == true &&
            (scratch || path.isNotBlank())
}

class CreateSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AoEApplication).container
    private val repository = container.repository

    private val _ui = MutableStateFlow(CreateUiState())
    val ui: StateFlow<CreateUiState> = _ui.asStateFlow()

    private val idempotencyKey = UUID.randomUUID().toString()
    private var progressJob: Job? = null
    private var pathJob: Job? = null
    private var pendingRequest: CreateSessionRequest? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, loadError = null)
            val agentsResult = repository.fetchAgents()
            val homeResult = repository.fetchHome()
            val recentResult = repository.fetchRecentProjects()
            val catalogResult = repository.fetchOptionCatalog()

            val agents = (agentsResult as? ApiResult.Ok)?.value.orEmpty()
            val installed = agents.filter { it.installed }
            val preferred = installed.firstOrNull { it.name == "opencode" }
                ?: installed.firstOrNull { it.name == "claude" }
                ?: installed.firstOrNull()
                ?: agents.firstOrNull()

            val home = (homeResult as? ApiResult.Ok)?.value.orEmpty()
            val recent = (recentResult as? ApiResult.Ok)?.value.orEmpty()
            val catalog = (catalogResult as? ApiResult.Ok)?.value.orEmpty()

            _ui.value = _ui.value.copy(
                loading = false,
                loadError = when {
                    agentsResult is ApiResult.Err -> agentsResult.message
                    agents.isEmpty() -> "Aucun agent disponible"
                    else -> null
                },
                agents = agents,
                selectedAgent = preferred?.name,
                optionCatalog = catalog,
                home = home,
                recentProjects = recent,
                structured = preferred?.canRunStructured ?: false,
                yoloMode = preferred?.let { it.alwaysYolo || it.acpAlwaysYolo } ?: false,
                browsePath = home,
            )
        }
    }

    fun selectAgent(name: String) {
        val agent = _ui.value.agents.firstOrNull { it.name == name } ?: return
        _ui.value = _ui.value.copy(
            selectedAgent = name,
            structured = agent.canRunStructured,
            yoloMode = agent.alwaysYolo || agent.acpAlwaysYolo,
            model = null,
            effort = null,
        )
    }

    fun setStructured(value: Boolean) {
        val agent = _ui.value.selectedAgentInfo
        if (value && agent?.canRunStructured != true) return
        _ui.value = _ui.value.copy(
            structured = value,
            yoloMode = if (agent != null && (agent.alwaysYolo || agent.acpAlwaysYolo)) true else _ui.value.yoloMode,
        )
    }

    fun setTitle(value: String) {
        _ui.value = _ui.value.copy(title = value)
    }

    fun setGroup(value: String) {
        _ui.value = _ui.value.copy(group = value)
    }

    fun setModel(value: String?) {
        _ui.value = _ui.value.copy(model = value)
    }

    fun setEffort(value: String?) {
        _ui.value = _ui.value.copy(effort = value)
    }

    fun setYolo(value: Boolean) {
        _ui.value = _ui.value.copy(yoloMode = value)
    }

    fun setWorktree(value: Boolean) {
        if (value && _ui.value.pathIsRepo != true) return
        _ui.value = _ui.value.copy(worktree = value)
        if (value && _ui.value.branches.isEmpty()) loadBranches(_ui.value.path)
    }

    fun setCreateNewBranch(value: Boolean) {
        _ui.value = _ui.value.copy(createNewBranch = value)
    }

    fun setWorktreeBranch(value: String) {
        _ui.value = _ui.value.copy(worktreeBranch = value)
    }

    fun setBaseBranch(value: String) {
        _ui.value = _ui.value.copy(baseBranch = value)
    }

    fun setScratch(value: Boolean) {
        _ui.value = _ui.value.copy(
            scratch = value,
            worktree = if (value) false else _ui.value.worktree,
        )
        if (!value) {
            pathChanged(_ui.value.path)
        }
    }

    fun selectRecentProject(project: RecentProject) {
        _ui.value = _ui.value.copy(
            path = project.path,
            scratch = false,
            title = _ui.value.title.ifBlank { project.displayName },
        )
        if (_ui.value.agents.any { it.name == project.tool && it.installed }) {
            selectAgent(project.tool)
        }
        pathChanged(project.path)
    }

    fun setPath(path: String) {
        _ui.value = _ui.value.copy(path = path, scratch = false)
        pathChanged(path)
    }

    private fun pathChanged(path: String) {
        pathJob?.cancel()
        _ui.value = _ui.value.copy(pathIsRepo = null, branches = emptyList(), worktree = false)
        if (path.isBlank()) return
        pathJob = viewModelScope.launch {
            when (val result = repository.isGitRepo(path)) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(pathIsRepo = result.value)
                    if (result.value) loadBranches(path)
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(pathIsRepo = false)
            }
        }
    }

    private fun loadBranches(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.fetchBranches(path)) {
                is ApiResult.Ok -> {
                    val current = result.value.firstOrNull { it.isCurrent }?.name.orEmpty()
                    _ui.value = _ui.value.copy(
                        branches = result.value,
                        baseBranch = _ui.value.baseBranch.ifBlank { current },
                    )
                }
                is ApiResult.Err -> Unit
            }
        }
    }

    fun openFolderPicker() {
        val start = _ui.value.path.ifBlank { _ui.value.home }
        _ui.value = _ui.value.copy(showFolderPicker = true, browsePath = start)
        browse(start)
    }

    fun closeFolderPicker() {
        _ui.value = _ui.value.copy(showFolderPicker = false)
    }

    fun browse(path: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(browseLoading = true, browseError = null, browsePath = path)
            when (val result = repository.browse(path, _ui.value.browseShowHidden)) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(
                        browseEntries = result.value.entries,
                        browseHasMore = result.value.hasMore,
                        browseLoading = false,
                    )
                }
                is ApiResult.Err -> {
                    _ui.value = _ui.value.copy(browseLoading = false, browseError = result.message)
                }
            }
        }
    }

    fun browseUp() {
        val path = _ui.value.browsePath.trimEnd('/')
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotBlank()) browse(parent)
    }

    fun browseHome() {
        if (_ui.value.home.isNotBlank()) browse(_ui.value.home)
    }

    fun setBrowseHidden(value: Boolean) {
        _ui.value = _ui.value.copy(browseShowHidden = value)
        browse(_ui.value.browsePath)
    }

    fun chooseCurrentFolder() {
        val path = _ui.value.browsePath
        if (path.isNotBlank()) {
            _ui.value = _ui.value.copy(showFolderPicker = false)
            setPath(path)
        }
    }

    fun startCreate() {
        if (!_ui.value.canCreate) return
        val state = _ui.value
        val request = buildRequest(state, idempotencyKey)
        pendingRequest = request
        _ui.value = _ui.value.copy(creating = true, createError = null, progress = null)
        viewModelScope.launch {
            when (val review = repository.reviewCreationTrust(request.path, request.scratch)) {
                is ApiResult.Ok -> {
                    val trust = review.value
                    if (trust != null && trust.requiresTrust) {
                        pendingRequest = request.copy(trustReview = trust.fingerprint)
                        _ui.value = _ui.value.copy(creating = false, trust = trust)
                    } else {
                        submit(request)
                    }
                }
                is ApiResult.Err -> {
                    _ui.value = _ui.value.copy(creating = false, createError = review.message)
                }
            }
        }
    }

    fun approveTrust() {
        val review = _ui.value.trust ?: return
        val request = pendingRequest ?: return
        _ui.value = _ui.value.copy(trust = null, trustApplying = true, creating = true)
        viewModelScope.launch {
            submit(request.copy(trustHooks = true, trustReview = review.fingerprint))
        }
    }

    fun skipTrust() {
        val request = pendingRequest ?: return
        _ui.value = _ui.value.copy(trust = null, trustApplying = true, creating = true)
        viewModelScope.launch {
            submit(request.copy(trustHooks = false, trustReview = null))
        }
    }

    fun cancelTrust() {
        pendingRequest = null
        _ui.value = _ui.value.copy(trust = null, creating = false)
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(createError = null)
    }

    fun dismissLoadError() {
        _ui.value = _ui.value.copy(loadError = null)
    }

    fun consumeCreated() {
        _ui.value = _ui.value.copy(createdSessionId = null)
    }

    private suspend fun submit(request: CreateSessionRequest) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(700)
                when (val result = repository.createProgress(request.idempotencyKey)) {
                    is ApiResult.Ok -> result.value?.let { progress ->
                        _ui.value = _ui.value.copy(progress = progress)
                    }
                    is ApiResult.Err -> Unit
                }
            }
        }
        val result = repository.createSession(request)
        progressJob?.cancel()
        progressJob = null
        when (result) {
            is ApiResult.Ok -> {
                val value = result.value
                when {
                    value.session != null -> {
                        container.sessionStore.upsert(value.session)
                        _ui.value = _ui.value.copy(
                            creating = false,
                            trustApplying = false,
                            progress = null,
                            createdSessionId = value.session.id,
                        )
                        repository.refresh()
                    }
                    value.hooksRequired != null -> {
                        pendingRequest = request
                        _ui.value = _ui.value.copy(
                            creating = false,
                            trustApplying = false,
                            progress = null,
                            trust = value.hooksRequired,
                        )
                    }
                    value.trustChanged -> {
                        _ui.value = _ui.value.copy(
                            creating = false,
                            trustApplying = false,
                            progress = null,
                            createError = "La configuration des hooks a changé. Relancez la création.",
                        )
                    }
                    else -> {
                        _ui.value = _ui.value.copy(
                            creating = false,
                            trustApplying = false,
                            progress = null,
                            createError = value.error ?: "Création impossible",
                        )
                    }
                }
            }
            is ApiResult.Err -> {
                _ui.value = _ui.value.copy(
                    creating = false,
                    trustApplying = false,
                    progress = null,
                    createError = result.message,
                )
            }
        }
    }

    private fun buildRequest(state: CreateUiState, key: String): CreateSessionRequest {
        val agent = state.selectedAgent ?: ""
        val worktree = state.worktree && state.pathIsRepo == true && !state.scratch
        return CreateSessionRequest(
            path = if (state.scratch) "" else state.path.trim(),
            tool = agent,
            title = state.title.trim().ifBlank { null },
            group = state.group.trim().ifBlank { null },
            structured = state.structured && state.selectedAgentInfo?.canRunStructured == true,
            agentModel = state.model,
            agentEffort = state.effort,
            yoloMode = state.yoloMode,
            worktreeEnabled = worktree,
            worktreeBranch = if (worktree) state.worktreeBranch.trim().ifBlank { null } else null,
            createNewBranch = worktree && state.createNewBranch,
            baseBranch = if (worktree && state.createNewBranch) state.baseBranch.ifBlank { null } else null,
            scratch = state.scratch,
            idempotencyKey = key,
        )
    }

    override fun onCleared() {
        progressJob?.cancel()
        pathJob?.cancel()
        super.onCleared()
    }
}
