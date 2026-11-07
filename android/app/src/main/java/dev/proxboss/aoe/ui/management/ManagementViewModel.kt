package dev.proxboss.aoe.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.proxboss.aoe.AoEApplication
import dev.proxboss.aoe.model.AboutInfo
import dev.proxboss.aoe.model.DeviceInfo
import dev.proxboss.aoe.model.LogsSnapshot
import dev.proxboss.aoe.model.McpServerInput
import dev.proxboss.aoe.model.McpServersView
import dev.proxboss.aoe.model.PasskeyInfo
import dev.proxboss.aoe.model.PluginList
import dev.proxboss.aoe.model.PluginUpdateInfo
import dev.proxboss.aoe.model.ProfileInfo
import dev.proxboss.aoe.model.SkillDetail
import dev.proxboss.aoe.model.SkillsView
import dev.proxboss.aoe.model.SystemHealth
import dev.proxboss.aoe.model.UpdateStatusInfo
import dev.proxboss.aoe.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ManagementTab(val label: String) {
    SERVER("Serveur"),
    MCP("MCP"),
    SKILLS("Skills"),
    PLUGINS("Plugins"),
    PROFILES("Profils"),
    SECURITY("Sécurité"),
}

data class ManagementUiState(
    val tab: ManagementTab = ManagementTab.SERVER,
    val about: AboutInfo? = null,
    val health: SystemHealth? = null,
    val update: UpdateStatusInfo? = null,
    val logs: LogsSnapshot? = null,
    val logsError: String? = null,
    val mcp: McpServersView? = null,
    val mcpError: String? = null,
    val skills: SkillsView? = null,
    val skillsError: String? = null,
    val skillDetail: SkillDetail? = null,
    val skillDetailLoading: Boolean = false,
    val plugins: PluginList? = null,
    val pluginsError: String? = null,
    val pluginUpdates: List<PluginUpdateInfo> = emptyList(),
    val profiles: List<ProfileInfo> = emptyList(),
    val profilesLoaded: Boolean = false,
    val profilesError: String? = null,
    val devices: List<DeviceInfo> = emptyList(),
    val passkeys: List<PasskeyInfo> = emptyList(),
    val securityLoaded: Boolean = false,
    val securityError: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class ManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AoEApplication).container.repository
    private val _ui = MutableStateFlow(ManagementUiState())
    val ui: StateFlow<ManagementUiState> = _ui.asStateFlow()

    init {
        refreshCurrent()
    }

    fun selectTab(tab: ManagementTab) {
        _ui.value = _ui.value.copy(tab = tab)
        refreshCurrent()
    }

    fun refreshCurrent() {
        when (_ui.value.tab) {
            ManagementTab.SERVER -> loadServer()
            ManagementTab.MCP -> loadMcp()
            ManagementTab.SKILLS -> loadSkills()
            ManagementTab.PLUGINS -> loadPlugins()
            ManagementTab.PROFILES -> loadProfiles()
            ManagementTab.SECURITY -> loadSecurity()
        }
    }

    fun loadServer() {
        viewModelScope.launch {
            val about = repository.fetchAbout()
            val health = repository.fetchHealth()
            val update = repository.fetchUpdateStatus()
            val logs = repository.fetchLogs()
            _ui.value = _ui.value.copy(
                about = (about as? ApiResult.Ok)?.value ?: _ui.value.about,
                health = (health as? ApiResult.Ok)?.value ?: _ui.value.health,
                update = (update as? ApiResult.Ok)?.value ?: _ui.value.update,
                logs = (logs as? ApiResult.Ok)?.value ?: _ui.value.logs,
                logsError = (logs as? ApiResult.Err)?.message,
            )
        }
    }

    fun loadMcp() {
        viewModelScope.launch {
            when (val result = repository.fetchMcpServers()) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(mcp = result.value, mcpError = null)
                is ApiResult.Err -> _ui.value = _ui.value.copy(mcpError = result.message)
            }
        }
    }

    fun saveMcp(name: String, input: McpServerInput, create: Boolean) {
        viewModelScope.launch {
            val result = if (create) repository.createMcpServer(name, input) else repository.updateMcpServer(name, input)
            afterMutation(result, if (create) "Serveur MCP créé" else "Serveur MCP mis à jour") { loadMcp() }
        }
    }

    fun deleteMcp(name: String) {
        viewModelScope.launch {
            afterMutation(repository.deleteMcpServer(name), "Serveur MCP supprimé") { loadMcp() }
        }
    }

    fun resolveMcpConflict(name: String, agent: String, winner: String, fingerprint: String) {
        viewModelScope.launch {
            afterMutation(
                repository.resolveMcpConflict(name, agent, winner, fingerprint),
                "Conflit MCP résolu",
            ) { loadMcp() }
        }
    }

    fun keepMcp(name: String, agent: String) {
        viewModelScope.launch {
            afterMutation(repository.keepMcpServer(name, agent), "Serveur MCP conservé") { loadMcp() }
        }
    }

    fun dropMcp(name: String, agent: String) {
        viewModelScope.launch {
            afterMutation(repository.dropMcpServer(name, agent), "Serveur MCP retiré") { loadMcp() }
        }
    }

    fun loadSkills() {
        viewModelScope.launch {
            when (val result = repository.fetchSkills()) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(skills = result.value, skillsError = null)
                is ApiResult.Err -> _ui.value = _ui.value.copy(skillsError = result.message)
            }
        }
    }

    fun openSkill(source: String, directory: String) {
        _ui.value = _ui.value.copy(skillDetailLoading = true, skillDetail = null)
        viewModelScope.launch {
            when (val result = repository.fetchSkillDetail(source, directory)) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(skillDetail = result.value, skillDetailLoading = false)
                is ApiResult.Err -> _ui.value = _ui.value.copy(
                    skillDetailLoading = false,
                    skillsError = result.message,
                )
            }
        }
    }

    fun closeSkill() {
        _ui.value = _ui.value.copy(skillDetail = null)
    }

    fun createSkill(directory: String, description: String?) {
        viewModelScope.launch {
            afterMutation(repository.createSkill(directory, description), "Skill créé") { loadSkills() }
        }
    }

    fun updateSkill(directory: String, content: String) {
        viewModelScope.launch {
            afterMutation(repository.updateSkill(directory, content), "Skill enregistré") {
                closeSkill()
                loadSkills()
            }
        }
    }

    fun deleteSkill(directory: String) {
        viewModelScope.launch {
            afterMutation(repository.deleteSkill(directory), "Skill supprimé") {
                closeSkill()
                loadSkills()
            }
        }
    }

    fun adoptSkill(source: String, directory: String, destination: String?) {
        viewModelScope.launch {
            afterMutation(repository.adoptSkill(source, directory, destination), "Skill adopté") { loadSkills() }
        }
    }

    fun loadPlugins() {
        viewModelScope.launch {
            val plugins = repository.fetchPlugins()
            val updates = repository.fetchPluginUpdates()
            _ui.value = _ui.value.copy(
                plugins = (plugins as? ApiResult.Ok)?.value ?: _ui.value.plugins,
                pluginsError = (plugins as? ApiResult.Err)?.message,
                pluginUpdates = (updates as? ApiResult.Ok)?.value.orEmpty(),
            )
        }
    }

    fun setPluginEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            afterMutation(
                repository.setPluginEnabled(id, enabled),
                if (enabled) "Plugin activé" else "Plugin désactivé",
            ) { loadPlugins() }
        }
    }

    fun loadProfiles() {
        viewModelScope.launch {
            when (val result = repository.fetchProfiles()) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(
                    profiles = result.value,
                    profilesLoaded = true,
                    profilesError = null,
                )
                is ApiResult.Err -> _ui.value = _ui.value.copy(
                    profilesLoaded = true,
                    profilesError = result.message,
                )
            }
        }
    }

    fun createProfile(name: String) {
        viewModelScope.launch {
            afterMutation(repository.createProfile(name), "Profil créé") { loadProfiles() }
        }
    }

    fun renameProfile(name: String, newName: String) {
        viewModelScope.launch {
            afterMutation(repository.renameProfile(name, newName), "Profil renommé") { loadProfiles() }
        }
    }

    fun setDefaultProfile(name: String) {
        viewModelScope.launch {
            afterMutation(repository.setDefaultProfile(name), "Profil par défaut mis à jour") { loadProfiles() }
        }
    }

    fun deleteProfile(name: String) {
        viewModelScope.launch {
            afterMutation(repository.deleteProfile(name), "Profil supprimé") { loadProfiles() }
        }
    }

    fun loadSecurity() {
        viewModelScope.launch {
            val devices = repository.fetchDevices()
            val passkeys = repository.fetchPasskeys()
            _ui.value = _ui.value.copy(
                devices = (devices as? ApiResult.Ok)?.value.orEmpty(),
                passkeys = (passkeys as? ApiResult.Ok)?.value.orEmpty(),
                securityLoaded = true,
                securityError = when {
                    devices is ApiResult.Err -> devices.message
                    passkeys is ApiResult.Err -> passkeys.message
                    else -> null
                },
            )
        }
    }

    fun revokeDevice(sessionId: String) {
        viewModelScope.launch {
            afterMutation(repository.revokeDevice(sessionId), "Session de login révoquée") { loadSecurity() }
        }
    }

    fun deletePasskey(credentialId: String) {
        viewModelScope.launch {
            afterMutation(repository.deletePasskey(credentialId), "Passkey supprimée") { loadSecurity() }
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    private suspend fun afterMutation(result: ApiResult<Unit>, success: String, reload: () -> Unit) {
        when (result) {
            is ApiResult.Ok -> {
                _ui.value = _ui.value.copy(message = success)
                reload()
            }
            is ApiResult.Err -> _ui.value = _ui.value.copy(message = result.message)
        }
    }
}
