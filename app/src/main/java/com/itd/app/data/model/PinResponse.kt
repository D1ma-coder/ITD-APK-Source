package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PinResponse(
    val success: Boolean,
    val pinnedPostId: String? = null,
)
