package dev.proxboss.aoe.notify

import dev.proxboss.aoe.model.NotifyEvent
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NotificationPolicyTest {
    private val quietOff = QuietHours(enabled = false, startMinute = 0, endMinute = 0)

    @Test
    fun `first snapshot only establishes a baseline`() {
        val policy = NotificationPolicy()
        val waiting = session("s1", SessionStatus.WAITING)
        assertTrue(policy.onSnapshot(listOf(waiting), now = 0, quietHours = quietOff).isEmpty())
        assertTrue(policy.onSnapshot(listOf(waiting), now = 60_000, quietHours = quietOff).isEmpty())
        assertNull(policy.onSoundDue(waiting, NotifyEvent.WAITING, 60_000, quietOff))
    }

    @Test
    fun `waiting alerts after its five second dwell and only once`() {
        val policy = NotificationPolicy()
        policy.onSnapshot(listOf(session("s1", SessionStatus.RUNNING)), 0, quietOff)
        val waiting = session("s1", SessionStatus.WAITING)

        assertTrue(policy.onSnapshot(listOf(waiting), 1_000, quietOff).isEmpty())
        assertTrue(policy.onSnapshot(listOf(waiting), 5_999, quietOff).isEmpty())
        val alerts = policy.onSnapshot(listOf(waiting), 6_000, quietOff)
        assertEquals(1, alerts.size)
        assertEquals(NotifyEvent.WAITING, alerts.first().event)
        assertEquals("s1", alerts.first().sessionId)
        assertTrue(policy.onSnapshot(listOf(waiting), 7_000, quietOff).isEmpty())
        assertNull(policy.onSoundDue(waiting, NotifyEvent.WAITING, 7_000, quietOff))
    }

    @Test
    fun `idle and error use the shorter dwell`() {
        val policy = NotificationPolicy()
        policy.onSnapshot(listOf(session("s1", SessionStatus.RUNNING)), 0, quietOff)
        val idle = session("s1", SessionStatus.IDLE)
        assertTrue(policy.onSnapshot(listOf(idle), 1_000, quietOff).isEmpty())
        assertEquals(1, policy.onSnapshot(listOf(idle), 3_000, quietOff).size)

        val errorPolicy = NotificationPolicy()
        errorPolicy.onSnapshot(listOf(session("s2", SessionStatus.RUNNING)), 0, quietOff)
        val error = session("s2", SessionStatus.ERROR)
        assertTrue(errorPolicy.onSnapshot(listOf(error), 1_000, quietOff).isEmpty())
        assertEquals(1, errorPolicy.onSnapshot(listOf(error), 3_000, quietOff).size)
    }

    @Test
    fun `per session mute suppresses the alert without a late delivery`() {
        val policy = NotificationPolicy()
        policy.onSnapshot(listOf(session("s1", SessionStatus.RUNNING)), 0, quietOff)
        val muted = session("s1", SessionStatus.WAITING, notifyOnWaiting = false)
        assertTrue(policy.onSnapshot(listOf(muted), 0, quietOff).isEmpty())
        assertTrue(policy.onSnapshot(listOf(muted), 10_000, quietOff).isEmpty())
        assertNull(policy.onSoundDue(muted, NotifyEvent.WAITING, 10_000, quietOff))
    }

    @Test
    fun `quiet hours suppress and retire the alert`() {
        val quiet = QuietHours(enabled = true, startMinute = 0, endMinute = 1_439)
        val policy = NotificationPolicy()
        policy.onSnapshot(listOf(session("s1", SessionStatus.RUNNING)), 0, quietOff)
        val waiting = session("s1", SessionStatus.WAITING)
        assertTrue(policy.onSnapshot(listOf(waiting), 0, quiet).isEmpty())
        assertTrue(policy.onSnapshot(listOf(waiting), 20_000, quiet).isEmpty())
        assertNull(policy.onSoundDue(waiting, NotifyEvent.WAITING, 20_000, quiet))
    }

    @Test
    fun `the same event is cooled down for sixty seconds`() {
        val policy = NotificationPolicy()
        policy.onSnapshot(listOf(session("s1", SessionStatus.RUNNING)), 0, quietOff)
        val waiting = session("s1", SessionStatus.WAITING)
        policy.onSnapshot(listOf(waiting), 0, quietOff)
        assertEquals(1, policy.onSnapshot(listOf(waiting), 5_000, quietOff).size)

        val running = session("s1", SessionStatus.RUNNING)
        policy.onSnapshot(listOf(running), 6_000, quietOff)
        policy.onSnapshot(listOf(waiting), 6_000, quietOff)
        assertTrue(policy.onSnapshot(listOf(waiting), 11_000, quietOff).isEmpty())

        policy.onSnapshot(listOf(running), 60_000, quietOff)
        policy.onSnapshot(listOf(waiting), 60_000, quietOff)
        assertEquals(1, policy.onSnapshot(listOf(waiting), 65_000, quietOff).size)
    }

    @Test
    fun `a session appearing later is baselined, not announced`() {
        val policy = NotificationPolicy()
        policy.onSnapshot(emptyList(), 0, quietOff)
        val waiting = session("s1", SessionStatus.WAITING)
        assertTrue(policy.onSnapshot(listOf(waiting), 1_000, quietOff).isEmpty())
        assertTrue(policy.onSnapshot(listOf(waiting), 30_000, quietOff).isEmpty())
    }

    @Test
    fun `archived and snoozed sessions never alert`() {
        val policy = NotificationPolicy()
        policy.onSnapshot(listOf(session("s1", SessionStatus.RUNNING)), 0, quietOff)
        val archived = session("s1", SessionStatus.WAITING, archived = true)
        assertTrue(policy.onSnapshot(listOf(archived), 0, quietOff).isEmpty())
        assertTrue(policy.onSnapshot(listOf(archived), 30_000, quietOff).isEmpty())

        val snoozedPolicy = NotificationPolicy()
        snoozedPolicy.onSnapshot(listOf(session("s2", SessionStatus.RUNNING)), 0, quietOff)
        val snoozed = session("s2", SessionStatus.ERROR, snoozed = true)
        assertTrue(snoozedPolicy.onSnapshot(listOf(snoozed), 0, quietOff).isEmpty())
        assertTrue(snoozedPolicy.onSnapshot(listOf(snoozed), 30_000, quietOff).isEmpty())
    }

    @Test
    fun `sound due emits once for an observed transition`() {
        val policy = NotificationPolicy()
        policy.onSnapshot(listOf(session("s1", SessionStatus.RUNNING)), 0, quietOff)
        val waiting = session("s1", SessionStatus.WAITING)
        policy.onSnapshot(listOf(waiting), 0, quietOff)

        val alert = policy.onSoundDue(waiting, NotifyEvent.WAITING, 2_000, quietOff)
        assertNotNull(alert)
        assertEquals(NotifyEvent.WAITING, alert?.event)
        assertNull(policy.onSoundDue(waiting, NotifyEvent.WAITING, 3_000, quietOff))
        assertNull(policy.onSoundDue(waiting, NotifyEvent.IDLE, 3_000, quietOff))
    }

    private fun session(
        id: String,
        status: SessionStatus,
        notifyOnWaiting: Boolean? = null,
        archived: Boolean = false,
        snoozed: Boolean = false,
    ): Session = Session(
        id = id,
        title = "Session $id",
        projectPath = "/work/$id",
        groupPath = "",
        tool = "claude",
        agent = "claude",
        status = status,
        dormant = false,
        favorited = false,
        pinnedAt = null,
        archivedAt = if (archived) "2026-01-01T00:00:00Z" else null,
        snoozedUntil = if (snoozed) "2099-01-01T00:00:00Z" else null,
        trashedAt = null,
        unread = false,
        notifyOnWaiting = notifyOnWaiting,
        notifyOnIdle = null,
        notifyOnError = null,
        view = "structured",
        acpCapable = true,
        acpWorkerState = "running",
        hasTerminal = false,
        lastAccessedAt = Instant.parse("2026-01-01T00:00:00Z"),
        idleEnteredAt = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastError = null,
        branch = null,
    )
}
