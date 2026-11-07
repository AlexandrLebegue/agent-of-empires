package dev.proxboss.aoe.notify

import dev.proxboss.aoe.model.NotifyEvent
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.model.SessionStatus

data class NotificationAlert(
    val sessionId: String,
    val title: String,
    val event: NotifyEvent,
    val status: SessionStatus,
)

/**
 * Mirrors the server push policy: only a status that remains stable for its
 * dwell (5 s for Waiting, 2 s for Idle/Error) alerts, each session/event pair
 * is cooled down, and the first observation of a session is only a baseline.
 */
class NotificationPolicy(
    private val waitingDwellMs: Long = 5_000,
    private val terminalDwellMs: Long = 2_000,
    private val cooldownMs: Long = 60_000,
) {
    private data class Tracked(
        val status: SessionStatus,
        val since: Long,
        var notified: Boolean,
    )

    private val tracked = mutableMapOf<String, Tracked>()
    private val lastNotified = mutableMapOf<String, Pair<NotifyEvent, Long>>()

    fun reset() {
        tracked.clear()
        lastNotified.clear()
    }

    fun onSnapshot(
        sessions: List<Session>,
        now: Long,
        quietHours: QuietHours,
    ): List<NotificationAlert> {
        val presentIds = sessions.mapTo(HashSet()) { it.id }
        tracked.keys.retainAll { it in presentIds }

        val alerts = ArrayList<NotificationAlert>()
        for (session in sessions) {
            val event = eventFor(session)
            val previous = tracked[session.id]
            if (previous == null) {
                tracked[session.id] = Tracked(session.status, now, notified = true)
                continue
            }
            val current = if (previous.status != session.status) {
                Tracked(session.status, now, notified = false).also { tracked[session.id] = it }
            } else {
                previous
            }
            if (current.notified) continue
            if (event == null) {
                current.notified = true
                continue
            }
            val dwell = if (event == NotifyEvent.WAITING) waitingDwellMs else terminalDwellMs
            if (now - current.since < dwell) continue

            // Suppressed events are retired too: a buzz delivered after quiet
            // hours or a mobile cooldown would describe a stale transition.
            current.notified = true
            if (quietHours.contains(now)) continue
            if (session.isMutedFor(event)) continue
            val last = lastNotified[session.id]
            if (last != null && last.first == event && now - last.second < cooldownMs) continue
            lastNotified[session.id] = event to now
            alerts.add(NotificationAlert(session.id, session.title, event, session.status))
        }
        return alerts
    }

    fun onSoundDue(
        session: Session,
        event: NotifyEvent,
        now: Long,
        quietHours: QuietHours,
    ): NotificationAlert? {
        val current = tracked[session.id] ?: return null
        if (current.status != session.status || current.notified) return null
        if (eventFor(session) != event) return null
        current.notified = true
        if (quietHours.contains(now)) return null
        if (session.isMutedFor(event)) return null
        val last = lastNotified[session.id]
        if (last != null && last.first == event && now - last.second < cooldownMs) return null
        lastNotified[session.id] = event to now
        return NotificationAlert(session.id, session.title, event, session.status)
    }

    private fun eventFor(session: Session): NotifyEvent? {
        if (session.isArchived || session.isSnoozed || session.isTrashed) return null
        return when (session.status) {
            SessionStatus.WAITING -> NotifyEvent.WAITING
            SessionStatus.IDLE -> NotifyEvent.IDLE
            SessionStatus.ERROR -> NotifyEvent.ERROR
            else -> null
        }
    }
}
