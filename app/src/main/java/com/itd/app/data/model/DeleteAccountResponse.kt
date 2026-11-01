package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DeleteAccountResponse(
    val deleted: Boolean,
    val message: String,
    val restoreDeadline: String? = null,
)
