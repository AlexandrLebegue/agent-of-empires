package dev.proxboss.aoe

import android.content.Context
import dev.proxboss.aoe.data.SessionRepository
import dev.proxboss.aoe.data.SessionStore
import dev.proxboss.aoe.data.SettingsRepository
import dev.proxboss.aoe.net.AoeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsRepository(appContext)
    val client = AoeClient()
    val sessionStore = SessionStore()
    val repository = SessionRepository(client, sessionStore, settings)
    val pendingOpenSession = MutableStateFlow<String?>(null)
    val authFailure = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    private var lastAuthFailureAt = 0L

    init {
        client.onUnauthorized = {
            val now = System.currentTimeMillis()
            if (now - lastAuthFailureAt > 60_000) {
                lastAuthFailureAt = now
                authFailure.tryEmit(Unit)
            }
        }
        scope.launch {
            settings.settings.collect { current ->
                client.configure(current.baseUrl, current.token)
            }
        }
        scope.launch {
            settings.models.collect { models ->
                sessionStore.setModels(models)
            }
        }
    }
}

class AoEApplication : android.app.Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
