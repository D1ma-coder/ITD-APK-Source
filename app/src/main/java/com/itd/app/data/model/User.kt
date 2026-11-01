package com.itd.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class User(
    val id: String = "",
    val username: String?,
    val displayName: String,
    val avatar: String?,
    val bio: String? = null,
    val verified: Boolean = false,
    val roles: List<String>? = null,
    val pin: UserPin? = null,
    val online: Boolean = false,
    val hasNuksta: Boolean = false,
    val subscription: UserSubscription? = null,
    val halloween: JsonElement? = null,
)
