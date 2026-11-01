package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AccountStatusResponse(
    val id: String? = null,
    val username: String? = null,
    val isDeleted: Boolean = false,
    val canRestore: Boolean = false,
    val restoreDeadline: String? = null,
    val subscription: UserSubscription? = null,
)
