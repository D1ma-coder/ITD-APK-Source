package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateReplyRequest(
    val content: String,
    val replyToUserId: String,
    val attachmentIds: List<String> = emptyList(),
)
