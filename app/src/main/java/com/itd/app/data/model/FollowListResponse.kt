package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FollowListResponse(
    val data: FollowListData,
)
