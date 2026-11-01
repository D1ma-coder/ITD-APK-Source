package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationItem(
    val id: String,
    val type: String,
    val targetType: String? = null,
    val targetId: String? = null,
    val subjectType: String? = null,
    val subjectId: String? = null,
    val preview: String? = null,
    val readAt: String? = null,
    val createdAt: String,
    val actor: NotificationActor,
    val read: Boolean = false,
    val sound: Boolean = false,
)
