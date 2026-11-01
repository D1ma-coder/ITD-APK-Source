package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationsResponse(
    val notifications: List<NotificationItem>,
    val hasMore: Boolean = false,
)
