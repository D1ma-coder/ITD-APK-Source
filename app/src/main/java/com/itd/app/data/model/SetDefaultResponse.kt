package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SetDefaultResponse(
    val success: Boolean? = null,
    val error: String? = null,
)
