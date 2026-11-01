package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationSettings(
    val enabled: Boolean = true,
    val sound: Boolean = true,
    val follows: Boolean = true,
    val wallPosts: Boolean = true,
    val likes: Boolean = true,
    val comments: Boolean = true,
    val mentions: Boolean = true,
)
