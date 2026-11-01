package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val updatedAt: String,
)
