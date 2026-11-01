package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChangePasswordResponse(
    val message: String,
)
