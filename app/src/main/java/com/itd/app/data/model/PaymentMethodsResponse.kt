package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PaymentMethodsResponse(
    val data: List<PaymentMethod>,
)
