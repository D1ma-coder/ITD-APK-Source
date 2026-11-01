package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DeleteMethodResponse(
    val success: Boolean? = null,
    val autoRenewalDisabled: Boolean? = null,
    val error: String? = null,
)
