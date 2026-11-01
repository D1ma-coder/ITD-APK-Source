package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthState(
    val authenticated: Boolean,
    val user: User? = null,
    val banned: Boolean = false,
    val profileRequired: Boolean = false,
    val userId: String? = null,
    val roles: List<String>? = null,
    val deleted: Boolean = false,
    val canRestore: Boolean = false,
    val restoreDeadline: String? = null,
)
