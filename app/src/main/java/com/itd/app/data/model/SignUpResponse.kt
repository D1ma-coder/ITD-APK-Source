package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SignUpResponse(
    val message: String,
    val email: String,
    val flowToken: String,
)
