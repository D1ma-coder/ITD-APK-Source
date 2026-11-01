package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateProfileRequest(
    val displayName: String,
    val avatar: String,
    val username: String,
)
