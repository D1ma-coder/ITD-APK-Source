package com.itd.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class UserProfile(
    val id: String,
    val username: String?,
    val displayName: String,
    val avatar: String?,
    val banner: String? = null,
    val bio: String? = null,
    val verified: Boolean = false,
    val pin: UserPin? = null,
    val pinnedPostId: String? = null,
    val wallAccess: String = "nobody",
    val likesVisibility: String = "everyone",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false,
    val createdAt: String? = null,
    val online: Boolean = false,
    val lastSeen: LastSeen? = null,
    val isBlockedByMe: Boolean = false,
    val hasNuksta: Boolean = false,
    val subscription: UserSubscription? = null,
    val halloween: JsonElement? = null,
)
