package com.itd.app.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import kotlin.math.abs
import kotlin.math.floor

object TimeFormat {
    private val lenient: DateTimeFormatter = DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .optionalStart().appendLiteral('T').optionalEnd()
        .optionalStart().appendLiteral(' ').optionalEnd()
        .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .optionalStart().appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
        .optionalEnd()
        .optionalStart().appendOffsetId().optionalEnd()
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
        .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
        .toFormatter()

    private val shortDate: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("dd/MM/yy")
        .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
        .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
        .toFormatter()

    /** "Только что", "5 мин.", "3 ч.", "2 дн.", "4 нед.", "1 г." */
    fun relative(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val seconds = abs(Instant.now().toEpochMilli() - parse(value).toEpochMilli()) / 1000.0
            if (seconds < 60) return "Только что"
            val minutes = seconds / 60
            if (floor(minutes).toLong() < 60) return "${floor(minutes).toLong()} мин."
            val hours = minutes / 60
            if (floor(hours).toLong() < 24) return "${floor(hours).toLong()} ч."
            val days = hours / 24
            if (floor(days).toLong() < 7) return "${floor(days).toLong()} дн."
            if (days < 365) return "${floor(days / 7).toLong()} нед."
            "${floor(days / 365).toLong()} г."
        } catch (e: Exception) {
            LogTag.APP.tree().w(e, "cannot format date %s", value)
            value
        }
    }

    /** Normalizes server timestamps ("2025-10-31 12:00:00.123456+03") to ISO-8601. */
    fun normalize(value: String): String {
        var result = value.replace(' ', 'T')
            .replace(Regex("(\\.\\d{3})\\d+"), "$1")
            .replace(Regex("([+-]\\d{2})$"), "$1:00")
        if ('Z' in result || '+' in result) return result
        if (!Regex(".*-\\d{2}:\\d{2}$").matches(result)) result += "Z"
        return result
    }

    fun parse(value: String): Instant = try {
        try {
            OffsetDateTime.parse(normalize(value)).toInstant()
        } catch (_: Exception) {
            shortDate.withZone(ZoneId.of("UTC")).parse(value, Instant::from)
        }
    } catch (_: Exception) {
        val withOffset = if (Regex(".*[+-]\\d{2}$").matches(value)) "$value:00" else value
        Instant.from(lenient.parse(withOffset.replace(' ', 'T')))
    }

    /** Russian plural form: 1 подписчик, 2 подписчика, 5 подписчиков. */
    fun plural(count: Int, one: String, few: String, many: String): String {
        val mod100 = abs(count) % 100
        val mod10 = mod100 % 10
        if (mod100 !in 11..19) {
            if (mod10 == 1) return one
            if (mod10 in 2..4) return few
        }
        return many
    }
}
