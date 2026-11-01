package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PaymentMethod(
    val id: String,
    val type: String,
    val cardLast4: String? = null,
    val cardBrand: String? = null,
    val isDefault: Boolean = false,
    val createdAt: String? = null,
)
