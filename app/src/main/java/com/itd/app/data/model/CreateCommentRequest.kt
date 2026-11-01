package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateCommentRequest(
    val content: String,
    val attachmentIds: List<String> = emptyList(),
)
