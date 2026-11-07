package dev.proxboss.aoe.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoe.AoEApplication
import dev.proxboss.aoe.data.AppSettings
import dev.proxboss.aoe.data.DEFAULT_BASE_URL
import dev.proxboss.aoe.net.AoeClient
import dev.proxboss.aoe.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val message: String) : ConnectionTestState
    data class Failure(val message: String) : ConnectionTestState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AoEApplication).container

    val settings: StateFlow<AppSettings> = container.settings.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    private val _baseUrl = MutableStateFlow(DEFAULT_BASE_URL)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()

    init {
        viewModelScope.launch {
            val current = container.settings.current()
            _baseUrl.value = current.baseUrl
            _token.value = current.token
        }
    }

    fun updateBaseUrl(value: String) {
        _baseUrl.value = value
        _testState.value = ConnectionTestState.Idle
    }

    fun updateToken(value: String) {
        _token.value = value
        _testState.value = ConnectionTestState.Idle
    }

    fun testConnection() {
        if (_testState.value == ConnectionTestState.Testing) return
        _testState.value = ConnectionTestState.Testing
        viewModelScope.launch {
            val target = AoeClient.normalizeBaseUrl(_baseUrl.value)
            _testState.value = when (val result = container.repository.testConnection(target, _token.value)) {
                is ApiResult.Ok -> ConnectionTestState.Success(result.value)
                is ApiResult.Err -> ConnectionTestState.Failure(result.message)
            }
        }
    }

    fun saveConnection(onSaved: () -> Unit) {
        viewModelScope.launch {
            val target = AoeClient.normalizeBaseUrl(_baseUrl.value)
            container.settings.saveConnection(target, _token.value)
            container.client.configure(target, _token.value)
            onSaved()
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settings.setNotificationsEnabled(enabled) }
    }

    fun setQuietHours(enabled: Boolean, startMinute: Int, endMinute: Int) {
        viewModelScope.launch { container.settings.setQuietHours(enabled, startMinute, endMinute) }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { container.settings.setTheme(theme) }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            container.settings.logout()
            val current = container.settings.current()
            container.client.configure(current.baseUrl, "")
            onLoggedOut()
        }
    }
}
