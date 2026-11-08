package dev.proxboss.aoe.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import dev.proxboss.aoe.MainActivity
import dev.proxboss.aoe.R
import dev.proxboss.aoe.model.NotifyEvent

object NotificationHelper {
    const val CHANNEL_SERVICE = "aoe_service"
    const val CHANNEL_ALERTS = "aoe_session_alerts"
    const val SERVICE_NOTIFICATION_ID = 1
    const val EXTRA_SESSION_ID = "aoe.session_id"
    const val ACTION_REPLY = "dev.proxboss.aoe.REPLY"
    const val ACTION_APPROVE = "dev.proxboss.aoe.APPROVE"
    const val EXTRA_NONCE = "aoe.approval_nonce"
    const val KEY_REPLY_TEXT = "aoe.reply_text"
    const val GROUP_STATUS = "dev.proxboss.aoe.status"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val service = NotificationChannel(
            CHANNEL_SERVICE,
            "Connexion AoE",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Maintient la connexion aux sessions Agent of Empires"
            setShowBadge(false)
        }
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            "Alertes de session",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Approbations, erreurs et fins de run"
        }
        manager.createNotificationChannel(service)
        manager.createNotificationChannel(alerts)
    }

    fun serviceNotification(context: Context, connected: Boolean): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_aoe)
            .setColor(0xFF67E8F9.toInt())
            .setContentTitle("AoE Companion")
            .setContentText(if (connected) "Surveillance des sessions connectée" else "Reconnexion au serveur…")
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun postAlert(
        context: Context,
        alert: NotificationAlert,
        approvalNonce: String? = null,
        approvalTool: String? = null,
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SESSION_ID, alert.sessionId)
            data = Uri.parse("aoe://session/${alert.sessionId}")
        }
        val openPending = PendingIntent.getActivity(
            context,
            notificationId(alert.sessionId),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_SESSION_ID, alert.sessionId)
            data = Uri.parse("aoe://session/${alert.sessionId}/reply")
        }
        // RemoteInput results are attached by the system, so the broadcast
        // PendingIntent must stay mutable on Android 12+.
        val replyMutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val replyPending = PendingIntent.getBroadcast(
            context,
            notificationId(alert.sessionId) + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or replyMutability,
        )
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Répondre à l'agent")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Répondre",
            replyPending,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val label = when (alert.event) {
            NotifyEvent.WAITING -> if (approvalNonce != null) {
                "Approbation requise : ${approvalTool ?: "outil"}"
            } else {
                "Approbation requise"
            }
            NotifyEvent.ERROR -> "La session a signalé une erreur"
            NotifyEvent.IDLE -> "Run terminé"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_aoe)
            .setColor(0xFF67E8F9.toInt())
            .setContentTitle(alert.title)
            .setContentText(label)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$label\nOuvrez ou répondez directement."))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setGroup(GROUP_STATUS)
            .setCategory(
                when (alert.event) {
                    NotifyEvent.ERROR -> NotificationCompat.CATEGORY_ERROR
                    NotifyEvent.WAITING -> NotificationCompat.CATEGORY_REMINDER
                    NotifyEvent.IDLE -> NotificationCompat.CATEGORY_STATUS
                },
            )
            .setPriority(
                if (alert.event == NotifyEvent.IDLE) {
                    NotificationCompat.PRIORITY_DEFAULT
                } else {
                    NotificationCompat.PRIORITY_HIGH
                },
            )
        if (approvalNonce != null) {
            val approveIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_APPROVE
                putExtra(EXTRA_SESSION_ID, alert.sessionId)
                putExtra(EXTRA_NONCE, approvalNonce)
                data = Uri.parse("aoe://session/${alert.sessionId}/approve/$approvalNonce")
            }
            val approvePending = PendingIntent.getBroadcast(
                context,
                notificationId(alert.sessionId) + 2,
                approveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.checkbox_on_background,
                    "Approuver",
                    approvePending,
                ).build(),
            )
        }
        builder.addAction(replyAction)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_view,
                    "Ouvrir",
                    openPending,
                ).build(),
            )

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(alert.sessionId), builder.build())
        }
    }

    fun postReplyFailure(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_aoe)
            .setContentTitle("AoE Companion")
            .setContentText("La réponse n'a pas pu être envoyée.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(9_000_001, notification)
        }
    }

    fun notificationId(sessionId: String): Int = 1_000 + (sessionId.hashCode() and 0x7fffffff) % 1_000_000

}
