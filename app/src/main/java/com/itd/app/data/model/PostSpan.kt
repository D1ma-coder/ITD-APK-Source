package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PostSpan(
    val type: String,
    val length: Int,
    val offset: Int,
    val url: String? = null,
    val username: String? = null,
    val tag: String? = null,
)
