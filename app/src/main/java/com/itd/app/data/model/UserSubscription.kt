package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSubscription(
    val isActive: Boolean? = null,
    val expiresAt: String? = null,
    val autoRenewal: Boolean? = null,
)
