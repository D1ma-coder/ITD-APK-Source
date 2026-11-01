package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BlockedUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String? = null,
    val verified: Boolean = false,
    val blockedAt: String? = null,
    val hasNuksta: Boolean = false,
)
