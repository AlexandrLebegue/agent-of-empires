package dev.proxboss.aoe.ui.diff

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoe.AoEApplication
import dev.proxboss.aoe.data.DiffBase
import dev.proxboss.aoe.data.DiffFile
import dev.proxboss.aoe.data.DiffFileContent
import dev.proxboss.aoe.data.SessionFileContent
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DiffMode {
    LIST,
    FILE,
    FULL,
}

data class DiffUiState(
    val session: Session? = null,
    val files: List<DiffFile> = emptyList(),
    val bases: List<DiffBase> = emptyList(),
    val warning: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val selected: DiffFile? = null,
    val content: DiffFileContent? = null,
    val fileLoading: Boolean = false,
    val mode: DiffMode = DiffMode.LIST,
    val fullPatch: String = "",
    val fullLoading: Boolean = false,
    val fullProgress: String = "",
    val showFileContent: Boolean = false,
    val sessionFile: SessionFileContent? = null,
    val sessionFileLoading: Boolean = false,
)

class DiffViewModel(
    application: Application,
    private val sessionId: String,
) : AndroidViewModel(application) {
    private val container = (application as AoEApplication).container
    private val _ui = MutableStateFlow(DiffUiState())
    val ui: StateFlow<DiffUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            container.sessionStore.sessions.collect { sessions ->
                _ui.value = _ui.value.copy(session = sessions.firstOrNull { it.id == sessionId })
            }
        }
        viewModelScope.launch { container.repository.refresh() }
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            when (val result = container.repository.diffFiles(sessionId)) {
                is ApiResult.Ok -> {
                    _ui.value = _ui.value.copy(
                        files = result.value.files,
                        bases = result.value.bases,
                        warning = result.value.warning,
                        loading = false,
                        error = null,
                    )
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(loading = false, error = result.message)
            }
        }
    }

    fun openFile(file: DiffFile) {
        _ui.value = _ui.value.copy(
            selected = file,
            content = null,
            fileLoading = true,
            error = null,
            mode = DiffMode.FILE,
            showFileContent = false,
            sessionFile = null,
        )
        viewModelScope.launch {
            when (val result = container.repository.diffFile(sessionId, file.path, file.repoName)) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(content = result.value, fileLoading = false)
                is ApiResult.Err -> _ui.value = _ui.value.copy(fileLoading = false, error = result.message)
            }
        }
    }

    fun openRelative(delta: Int) {
        val state = _ui.value
        val current = state.selected ?: return
        val index = state.files.indexOfFirst { it.path == current.path && it.repoName == current.repoName }
        if (index < 0) return
        val next = index + delta
        if (next in state.files.indices) openFile(state.files[next])
    }

    fun closeFile() {
        _ui.value = _ui.value.copy(
            selected = null,
            content = null,
            error = null,
            mode = DiffMode.LIST,
            showFileContent = false,
            sessionFile = null,
        )
    }

    fun loadFullDiff() {
        if (_ui.value.fullLoading) return
        _ui.value = _ui.value.copy(mode = DiffMode.FULL, fullLoading = true, fullPatch = "", error = null)
        viewModelScope.launch {
            val files = _ui.value.files.take(80)
            val parts = ArrayList<String>()
            files.forEachIndexed { index, file ->
                _ui.value = _ui.value.copy(fullProgress = "${index + 1}/${files.size} · ${file.path}")
                when (val result = container.repository.diffFile(sessionId, file.path, file.repoName)) {
                    is ApiResult.Ok -> {
                        val content = result.value
                        parts.add(
                            buildString {
                                append("════ ").append(file.path).append(" ════\n")
                                when {
                                    content.isBinary -> append("(fichier binaire)\n")
                                    content.truncated -> append("(fichier tronqué)\n")
                                    content.patch.isBlank() -> append("(aucun contenu textuel)\n")
                                    else -> append(content.patch)
                                }
                            },
                        )
                    }
                    is ApiResult.Err -> parts.add("════ ${file.path} ════\nErreur : ${result.message}\n")
                }
            }
            if (_ui.value.files.size > files.size) {
                parts.add("\n(${_ui.value.files.size - files.size} fichiers supplémentaires non chargés.)\n")
            }
            _ui.value = _ui.value.copy(
                fullPatch = parts.joinToString("\n"),
                fullLoading = false,
                fullProgress = "",
            )
        }
    }

    fun showList() {
        _ui.value = _ui.value.copy(mode = DiffMode.LIST, showFileContent = false, sessionFile = null)
    }

    fun showFullFile() {
        val file = _ui.value.selected ?: return
        _ui.value = _ui.value.copy(showFileContent = true, sessionFileLoading = true, sessionFile = null)
        viewModelScope.launch {
            when (val result = container.repository.sessionFile(sessionId, file.path)) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(sessionFile = result.value, sessionFileLoading = false)
                is ApiResult.Err -> _ui.value = _ui.value.copy(sessionFileLoading = false, error = result.message)
            }
        }
    }

    fun showPatch() {
        _ui.value = _ui.value.copy(showFileContent = false, sessionFile = null)
    }

    fun setDiffBase(repoName: String?, baseBranch: String?) {
        viewModelScope.launch {
            when (val result = container.repository.setDiffBase(sessionId, baseBranch, repoName)) {
                is ApiResult.Ok -> loadFiles()
                is ApiResult.Err -> _ui.value = _ui.value.copy(error = result.message)
            }
        }
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }

    class Factory(
        private val application: Application,
        private val sessionId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiffViewModel(application, sessionId) as T
        }
    }
}
