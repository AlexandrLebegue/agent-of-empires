package dev.proxboss.aoe.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

const val DEFAULT_BASE_URL = "http://192.168.1.60:8080"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "aoe_settings")

data class AppSettings(
    val configured: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val token: String = "",
    val notificationsEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
    val theme: String = "system",
    val permissionAsked: Boolean = false,
) {
    override fun toString(): String =
        "AppSettings(configured=$configured, baseUrl=$baseUrl, token=***, " +
            "notificationsEnabled=$notificationsEnabled, quietHoursEnabled=$quietHoursEnabled)"
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val configured = booleanPreferencesKey("configured")
        val baseUrl = stringPreferencesKey("base_url")
        val token = stringPreferencesKey("token")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
        val quietStart = intPreferencesKey("quiet_start_minute")
        val quietEnd = intPreferencesKey("quiet_end_minute")
        val theme = stringPreferencesKey("theme")
        val permissionAsked = booleanPreferencesKey("permission_asked")
        val models = stringPreferencesKey("session_models")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences ->
            AppSettings(
                configured = preferences[Keys.configured] ?: false,
                baseUrl = preferences[Keys.baseUrl] ?: DEFAULT_BASE_URL,
                token = preferences[Keys.token] ?: "",
                notificationsEnabled = preferences[Keys.notificationsEnabled] ?: true,
                quietHoursEnabled = preferences[Keys.quietHoursEnabled] ?: false,
                quietStartMinute = preferences[Keys.quietStart] ?: (22 * 60),
                quietEndMinute = preferences[Keys.quietEnd] ?: (7 * 60),
                theme = preferences[Keys.theme] ?: "system",
                permissionAsked = preferences[Keys.permissionAsked] ?: false,
            )
        }

    val models: Flow<Map<String, String>> = context.settingsDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences -> parseModels(preferences[Keys.models]) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun saveConnection(baseUrl: String, token: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.baseUrl] = baseUrl.trim().trimEnd('/')
            preferences[Keys.token] = token.trim()
            preferences[Keys.configured] = true
        }
    }

    suspend fun logout() {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.token] = ""
            preferences[Keys.configured] = false
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.notificationsEnabled] = enabled }
    }

    suspend fun setQuietHours(enabled: Boolean, startMinute: Int, endMinute: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.quietHoursEnabled] = enabled
            preferences[Keys.quietStart] = startMinute.coerceIn(0, 1439)
            preferences[Keys.quietEnd] = endMinute.coerceIn(0, 1439)
        }
    }

    suspend fun setTheme(theme: String) {
        val allowed = theme.takeIf { it in setOf("system", "light", "dark") } ?: "system"
        context.settingsDataStore.edit { it[Keys.theme] = allowed }
    }

    suspend fun setPermissionAsked() {
        context.settingsDataStore.edit { it[Keys.permissionAsked] = true }
    }

    suspend fun putModel(sessionId: String, model: String) {
        if (sessionId.isBlank() || model.isBlank()) return
        context.settingsDataStore.edit { preferences ->
            val models = parseModels(preferences[Keys.models]).toMutableMap()
            if (models[sessionId] != model) {
                models[sessionId] = model
                preferences[Keys.models] = JSONObject().apply {
                    models.forEach { (id, value) -> put(id, value) }
                }.toString()
            }
        }
    }

    private fun parseModels(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = root.optString(key)
                    if (key.isNotBlank() && value.isNotBlank()) put(key, value)
                }
            }
        }.getOrDefault(emptyMap())
    }
}
