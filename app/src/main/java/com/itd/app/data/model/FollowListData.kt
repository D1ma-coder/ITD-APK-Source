package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FollowListData(
    val users: List<FollowListUser>,
    val pagination: FollowListPagination,
)
