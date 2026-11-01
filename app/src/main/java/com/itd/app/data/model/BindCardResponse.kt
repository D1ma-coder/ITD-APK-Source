package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BindCardResponse(
    val success: Boolean? = null,
    val confirmationUrl: String? = null,
    val error: String? = null,
)
