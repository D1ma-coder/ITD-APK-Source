package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class QrLoginTarget(
    val client: String? = null,
    val clientVersion: String? = null,
    val os: String? = null,
    val osVersion: String? = null,
    val deviceType: String? = null,
    val ipAddress: String? = null,
    val ipCountry: String? = null,
    val ipCity: String? = null,
    val requestedAt: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyKm: Double? = null,
    val precision: String? = null,
) {
    val clientTitle: String
        get() = when {
            client == null -> "Неизвестный браузер"
            clientVersion == null -> client
            else -> "$client $clientVersion"
        }

    val osTitle: String?
        get() = when {
            os == null -> null
            os == "macOS" -> "macOS"
            osVersion == null -> os
            else -> "$os $osVersion"
        }

    /** "Город, Страна" with the country name in the device language. */
    val locationTitle: String?
        get() {
            val parts = ArrayList<String>()
            ipCity?.let(parts::add)
            ipCountry?.let { code ->
                val name = java.util.Locale("", code).getDisplayCountry(java.util.Locale.getDefault())
                parts += name.ifEmpty { code }
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
        }

    val locationPrecision: LocationPrecision?
        get() = when (precision?.lowercase(java.util.Locale.ROOT)) {
            "city" -> LocationPrecision.CITY
            "country" -> LocationPrecision.COUNTRY
            else -> null
        }
}

enum class LocationPrecision { CITY, COUNTRY }
