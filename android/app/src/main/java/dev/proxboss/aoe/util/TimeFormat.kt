package dev.proxboss.aoe.util

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormat {
    fun parseInstant(value: String?): Instant? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
    }

    fun formatAge(at: Instant?, now: Instant = Instant.now()): String {
        if (at == null) return ""
        val seconds = Duration.between(at, now).seconds.coerceAtLeast(0)
        return when {
            seconds < 10 -> "à l'instant"
            seconds < 60 -> "il y a ${seconds}s"
            seconds < 3600 -> "il y a ${seconds / 60} min"
            seconds < 86_400 -> "il y a ${seconds / 3600} h"
            seconds < 7 * 86_400 -> "il y a ${seconds / 86_400} j"
            else -> DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.systemDefault()).format(at)
        }
    }

    fun formatClockMinute(minuteOfDay: Int): String {
        val normalized = ((minuteOfDay % 1440) + 1440) % 1440
        return "%02d:%02d".format(normalized / 60, normalized % 60)
    }
}
