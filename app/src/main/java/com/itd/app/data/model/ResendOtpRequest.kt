package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ResendOtpRequest(
    val email: String,
    val flowToken: String,
)
