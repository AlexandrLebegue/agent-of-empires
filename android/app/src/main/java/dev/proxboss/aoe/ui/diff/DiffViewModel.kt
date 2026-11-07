package dev.proxboss.aoe.ui.diff

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoe.AoEApplication
import dev.proxboss.aoe.data.DiffFile
import dev.proxboss.aoe.data.DiffFileContent
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiffUiState(
    val session: Session? = null,
    val files: List<DiffFile> = emptyList(),
    val warning: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val selected: DiffFile? = null,
    val content: DiffFileContent? = null,
    val fileLoading: Boolean = false,
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
        _ui.value = _ui.value.copy(selected = file, content = null, fileLoading = true, error = null)
        viewModelScope.launch {
            when (val result = container.repository.diffFile(sessionId, file.path, file.repoName)) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(content = result.value, fileLoading = false)
                is ApiResult.Err -> _ui.value = _ui.value.copy(fileLoading = false, error = result.message)
            }
        }
    }

    fun closeFile() {
        _ui.value = _ui.value.copy(selected = null, content = null, error = null)
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
