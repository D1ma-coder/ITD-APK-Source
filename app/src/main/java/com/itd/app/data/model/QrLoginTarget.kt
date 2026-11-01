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
)
