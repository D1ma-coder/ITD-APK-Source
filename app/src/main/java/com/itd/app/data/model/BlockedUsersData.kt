package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BlockedUsersData(
    val users: List<BlockedUser>,
    val pagination: BlockedUsersPagination,
)
