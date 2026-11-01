package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VersionInfoResponse(
    val android: PlatformVersion,
)
