package dev.proxboss.aoe.notify

import dev.proxboss.aoe.data.AppSettings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class QuietHours(
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
) {
    fun contains(time: LocalTime): Boolean {
        if (!enabled) return false
        val start = ((startMinute % 1440) + 1440) % 1440
        val end = ((endMinute % 1440) + 1440) % 1440
        if (start == end) return false
        val minutes = time.hour * 60 + time.minute
        return if (start < end) {
            minutes >= start && minutes < end
        } else {
            minutes >= start || minutes < end
        }
    }

    fun contains(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        contains(instant.atZone(zone).toLocalTime())

    fun contains(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        contains(Instant.ofEpochMilli(epochMillis), zone)

    companion object {
        fun from(settings: AppSettings): QuietHours = QuietHours(
            enabled = settings.quietHoursEnabled,
            startMinute = settings.quietStartMinute,
            endMinute = settings.quietEndMinute,
        )
    }
}
