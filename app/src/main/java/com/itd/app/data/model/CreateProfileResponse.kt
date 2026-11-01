package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateProfileResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String,
    val createdAt: String,
)
