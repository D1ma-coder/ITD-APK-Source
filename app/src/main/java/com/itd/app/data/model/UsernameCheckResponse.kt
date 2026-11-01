package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UsernameCheckResponse(
    val available: Boolean,
)
