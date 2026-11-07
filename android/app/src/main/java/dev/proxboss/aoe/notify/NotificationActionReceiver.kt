package dev.proxboss.aoe.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import dev.proxboss.aoe.AoEApplication
import dev.proxboss.aoe.net.ApiResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(NotificationHelper.EXTRA_SESSION_ID).orEmpty()
        if (sessionId.isBlank()) return
        when (intent.action) {
            NotificationHelper.ACTION_REPLY -> handleReply(context, intent, sessionId)
            NotificationHelper.ACTION_APPROVE -> handleApprove(context, intent, sessionId)
            else -> Unit
        }
    }

    private fun handleReply(context: Context, intent: Intent, sessionId: String) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val container = (appContext as AoEApplication).container
        container.scope.launch {
            try {
                val delivered = withTimeoutOrNull(9_000) {
                    val settings = container.settings.current()
                    if (!settings.configured) return@withTimeoutOrNull false
                    container.client.configure(settings.baseUrl, settings.token)
                    if (container.sessionStore.session(sessionId) == null) {
                        container.repository.refresh()
                    }
                    container.repository.sendPrompt(sessionId, text) is ApiResult.Ok
                }
                if (delivered == true) {
                    cancelNotification(appContext, sessionId)
                } else {
                    NotificationHelper.postReplyFailure(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleApprove(context: Context, intent: Intent, sessionId: String) {
        val nonce = intent.getStringExtra(NotificationHelper.EXTRA_NONCE).orEmpty()
        if (nonce.isBlank()) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val container = (appContext as AoEApplication).container
        container.scope.launch {
            try {
                val approved = withTimeoutOrNull(9_000) {
                    val settings = container.settings.current()
                    if (!settings.configured) return@withTimeoutOrNull false
                    container.client.configure(settings.baseUrl, settings.token)
                    container.repository.resolveApprovalNonce(sessionId, nonce, "Allow") is ApiResult.Ok
                }
                if (approved == true) {
                    cancelNotification(appContext, sessionId)
                } else {
                    NotificationHelper.postReplyFailure(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun cancelNotification(context: Context, sessionId: String) {
        runCatching {
            NotificationManagerCompat.from(context)
                .cancel(NotificationHelper.notificationId(sessionId))
        }
    }
}
