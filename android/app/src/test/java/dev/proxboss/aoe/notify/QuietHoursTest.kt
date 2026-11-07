package dev.proxboss.aoe.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class QuietHoursTest {
    @Test
    fun `disabled range never matches`() {
        val quiet = QuietHours(enabled = false, startMinute = 22 * 60, endMinute = 7 * 60)
        assertFalse(quiet.contains(LocalTime.of(23, 0)))
        assertFalse(quiet.contains(LocalTime.of(3, 0)))
    }

    @Test
    fun `same-day range includes start and excludes end`() {
        val quiet = QuietHours(enabled = true, startMinute = 9 * 60, endMinute = 17 * 60)
        assertFalse(quiet.contains(LocalTime.of(8, 59)))
        assertTrue(quiet.contains(LocalTime.of(9, 0)))
        assertTrue(quiet.contains(LocalTime.of(12, 30)))
        assertTrue(quiet.contains(LocalTime.of(16, 59)))
        assertFalse(quiet.contains(LocalTime.of(17, 0)))
    }

    @Test
    fun `range wrapping midnight covers late evening and early morning`() {
        val quiet = QuietHours(enabled = true, startMinute = 22 * 60, endMinute = 7 * 60)
        assertTrue(quiet.contains(LocalTime.of(22, 0)))
        assertTrue(quiet.contains(LocalTime.of(23, 59)))
        assertTrue(quiet.contains(LocalTime.of(0, 0)))
        assertTrue(quiet.contains(LocalTime.of(6, 59)))
        assertFalse(quiet.contains(LocalTime.of(7, 0)))
        assertFalse(quiet.contains(LocalTime.of(15, 0)))
    }

    @Test
    fun `zero length range is disabled`() {
        val quiet = QuietHours(enabled = true, startMinute = 600, endMinute = 600)
        assertFalse(quiet.contains(LocalTime.of(10, 0)))
    }

    @Test
    fun `minutes outside a day are normalized`() {
        val quiet = QuietHours(enabled = true, startMinute = 24 * 60 + 60, endMinute = 2 * 60)
        assertTrue(quiet.contains(LocalTime.of(1, 0)))
        assertFalse(quiet.contains(LocalTime.of(3, 0)))
    }
}
