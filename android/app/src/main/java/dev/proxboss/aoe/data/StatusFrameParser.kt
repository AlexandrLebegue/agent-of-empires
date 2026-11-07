package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.NotifyEvent
import dev.proxboss.aoe.model.SessionStatus
import org.json.JSONObject

sealed interface StatusFrame {
    data object Ready : StatusFrame
    data object Resync : StatusFrame
    data class Status(val id: String, val status: SessionStatus) : StatusFrame
    data class SoundDue(val id: String, val event: NotifyEvent) : StatusFrame
    data object Unknown : StatusFrame
}

object StatusFrameParser {
    fun parse(text: String): StatusFrame {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return StatusFrame.Unknown
        return when (root.optString("kind")) {
            "ready" -> StatusFrame.Ready
            "resync" -> StatusFrame.Resync
            "status" -> {
                val id = root.optString("id")
                val revision = root.optString("revision")
                val status = SessionStatus.fromWire(root.optString("status"))
                if (id.isEmpty() || status == SessionStatus.UNKNOWN || !revision.matches(Regex("\\d+"))) {
                    StatusFrame.Unknown
                } else {
                    StatusFrame.Status(id, status)
                }
            }
            "sound_due" -> {
                val id = root.optString("id")
                val event = NotifyEvent.fromWire(root.optString("event"))
                if (id.isEmpty() || event == null) StatusFrame.Unknown else StatusFrame.SoundDue(id, event)
            }
            else -> StatusFrame.Unknown
        }
    }
}
