package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreatePostRequest(
    val content: String,
    val attachmentIds: List<String> = emptyList(),
    val spans: List<PostSpan> = emptyList(),
    val poll: PostPollRequest? = null,
    val wallRecipientId: String? = null,
)
