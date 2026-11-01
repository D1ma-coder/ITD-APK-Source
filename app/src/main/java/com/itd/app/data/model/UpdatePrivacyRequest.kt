package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePrivacyRequest(
    val wallAccess: String,
    val likesVisibility: String,
    val messageAccess: String,
    val showLastSeen: Boolean,
)
