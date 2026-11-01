package com.itd.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SuggestedUser(
    val id: String,
    val username: String? = null,
    val displayName: String,
    val avatar: String? = null,
    val verified: Boolean = false,
    val followersCount: Int = 0,
    val hasNuksta: Boolean = false,
    val halloween: JsonElement? = null,
)
