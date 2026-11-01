package com.itd.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NotificationActor(
    val id: String,
    val displayName: String,
    val username: String? = null,
    val avatar: String? = null,
    val hasNuksta: Boolean = false,
    val isFollowing: Boolean? = null,
    val isFollowedBy: Boolean? = null,
    val halloween: JsonElement? = null,
)
