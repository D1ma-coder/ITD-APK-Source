package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AutoRenewalResponse(
    val success: Boolean? = null,
    val expiresAt: String? = null,
    val autoRenewal: Boolean? = null,
    val renewed: Boolean? = null,
    val error: String? = null,
)
