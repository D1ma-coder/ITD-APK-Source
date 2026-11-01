package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PrivacySettings(
    val isPrivate: Boolean = false,
    val wallAccess: String = "everyone",
    val likesVisibility: String = "everyone",
    val messageAccess: String = "everyone",
    val showLastSeen: Boolean = true,
)
