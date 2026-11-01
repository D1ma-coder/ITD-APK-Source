package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EditPostRequest(
    val content: String,
    val spans: List<PostSpan> = emptyList(),
)
