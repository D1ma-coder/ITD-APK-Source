package com.itd.app.halloween

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

internal object HalloweenJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun JsonElement?.field(name: String): JsonElement? = (this as? JsonObject)?.get(name)

    fun JsonElement?.array(): JsonArray? = this as? JsonArray

    fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

    fun JsonElement?.double(): Double? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull

    fun JsonElement?.int(): Int? {
        val value = double() ?: return null
        if (value != Math.rint(value) || Math.abs(value) > 9.007199254740991E15 || value > Int.MAX_VALUE || value < Int.MIN_VALUE) return null
        return value.toInt()
    }

    fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun epochMillis(value: String?): Long? {
        if (value == null) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
