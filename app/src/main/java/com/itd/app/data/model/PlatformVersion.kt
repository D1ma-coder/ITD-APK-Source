package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PlatformVersion(
    val minVersion: String,
    val latestVersion: String,
    val updateUrl: String,
)
