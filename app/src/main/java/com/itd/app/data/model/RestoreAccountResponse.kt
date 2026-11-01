package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RestoreAccountResponse(
    val restored: Boolean,
    val message: String,
)
