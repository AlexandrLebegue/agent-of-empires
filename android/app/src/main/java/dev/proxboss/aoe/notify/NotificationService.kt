package dev.proxboss.aoe.notify

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dev.proxboss.aoe.AoEApplication
import dev.proxboss.aoe.AppContainer
import dev.proxboss.aoe.data.AppSettings
import dev.proxboss.aoe.data.AcpFrame
import dev.proxboss.aoe.data.AcpFrameParser
import dev.proxboss.aoe.data.StatusFrame
import dev.proxboss.aoe.data.StatusFrameParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.coroutines.coroutineContext

class NotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var container: AppContainer
    private val policy = NotificationPolicy()

    @Volatile
    private var currentSettings = AppSettings()

    @Volatile
    private var connected = false

    private var socket: WebSocket? = null
    private var monitorJob: Job? = null
    private var settingsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as AoEApplication).container
        NotificationHelper.createChannels(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            NotificationHelper.serviceNotification(this, connected = false),
            type,
        )
        settingsJob = scope.launch {
            var lastConnection: Pair<String, String>? = null
            container.settings.settings.collect { settings ->
                currentSettings = settings
                container.client.configure(settings.baseUrl, settings.token)
                if (!settings.configured || !settings.notificationsEnabled) {
                    stopMonitoring()
                    ServiceCompat.stopForeground(this@NotificationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val connection = settings.baseUrl to settings.token
                    if (monitorJob?.isActive != true || lastConnection != connection) {
                        lastConnection = connection
                        stopMonitoring()
                        startMonitoring()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        settingsJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch { monitor() }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        socket?.close(1000, "service stopped")
        socket = null
        connected = false
    }

    private suspend fun monitor() {
        val socketJob = scope.launch { statusSocketLoop() }
        try {
            var nextRefresh = 0L
            while (coroutineContext.isActive) {
                val now = System.currentTimeMillis()
                if (now >= nextRefresh) {
                    container.repository.refresh()
                    nextRefresh = now + if (connected) 20_000 else 5_000
                }
                val quietHours = QuietHours.from(currentSettings)
                policy.onSnapshot(
                    container.sessionStore.sessions.value,
                    now,
                    quietHours,
                ).forEach { alert ->
                    scope.launch { notifyAlert(alert) }
                }
                delay(1_000)
            }
        } finally {
            socketJob.cancel()
        }
    }

    private suspend fun statusSocketLoop() {
        while (coroutineContext.isActive) {
            val signal = Channel<Unit>(Channel.CONFLATED)
            val socket = container.client.openStatusSocket(StatusListener(signal))
            if (socket == null) {
                delay(3_000)
                continue
            }
            this.socket = socket
            signal.receive()
            this.socket = null
            connected = false
            updateServiceNotification()
            if (!coroutineContext.isActive) break
            delay(RECONNECT_DELAYS[reconnectAttempt.coerceAtMost(RECONNECT_DELAYS.lastIndex)])
            reconnectAttempt++
        }
    }

    @Volatile
    private var reconnectAttempt = 0

    private inner class StatusListener(private val signal: Channel<Unit>) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            reconnectAttempt = 0
            updateServiceNotification()
            scope.launch { container.repository.refresh() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (val frame = StatusFrameParser.parse(text)) {
                StatusFrame.Ready, StatusFrame.Resync -> {
                    scope.launch { container.repository.refresh() }
                }
                is StatusFrame.Status -> {
                    container.sessionStore.applyStatus(frame.id, frame.status)
                    if (container.sessionStore.session(frame.id) == null) {
                        scope.launch { container.repository.refresh() }
                    }
                }
                is StatusFrame.SoundDue -> {
                    val session = container.sessionStore.session(frame.id)
                    if (session == null) {
                        scope.launch { container.repository.refresh() }
                    } else {
                        policy.onSoundDue(
                            session,
                            frame.event,
                            System.currentTimeMillis(),
                            QuietHours.from(currentSettings),
                        )?.let { alert ->
                            scope.launch { notifyAlert(alert) }
                        }
                    }
                }
                StatusFrame.Unknown -> Unit
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected = false
            updateServiceNotification()
            signal.trySend(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected = false
            signal.trySend(Unit)
        }
    }

    private suspend fun notifyAlert(alert: dev.proxboss.aoe.notify.NotificationAlert) {
        val approval = if (alert.event == dev.proxboss.aoe.model.NotifyEvent.WAITING) {
            pendingApprovalFor(alert.sessionId)
        } else {
            null
        }
        NotificationHelper.postAlert(this, alert, approval?.first, approval?.second)
    }

    private suspend fun pendingApprovalFor(sessionId: String): Pair<String, String>? {
        val deferred = CompletableDeferred<Pair<String, String>?>()
        val socket = container.client.openAcpSocket(sessionId, since = 0, listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = AcpFrameParser.parseLive(text)
                if (frame is AcpFrame.ReducedState) {
                    val approval = frame.payload.pendingApprovals?.firstOrNull()
                    deferred.complete(
                        approval?.let { it.nonce to it.toolCall.name.ifBlank { "outil" } },
                    )
                    webSocket.close(1000, "approval probe")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                deferred.complete(null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                deferred.complete(null)
            }
        }) ?: return null
        val result = withTimeoutOrNull(4_000) { deferred.await() }
        socket.close(1000, "approval probe")
        return result?.takeIf { it.first.isNotBlank() }
    }

    private fun updateServiceNotification() {
        runCatching {
            NotificationManagerCompat.from(this).notify(
                NotificationHelper.SERVICE_NOTIFICATION_ID,
                NotificationHelper.serviceNotification(this, connected),
            )
        }
    }

    companion object {
        private val RECONNECT_DELAYS = longArrayOf(2_000, 5_000, 10_000, 20_000, 30_000)
    }
}
