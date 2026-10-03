package com.cafetera

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object CoffeeTime {
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun label(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return try {
            clock.withZone(ZoneId.systemDefault()).format(Instant.parse(value))
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
