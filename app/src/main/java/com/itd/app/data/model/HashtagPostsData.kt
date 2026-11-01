package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class HashtagPostsData(
    val hashtag: TrendingHashtag,
    val posts: List<Post> = emptyList(),
    val pagination: FeedPagination,
)
