package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PostPoll(
    val id: String,
    val postId: String,
    val question: String,
    val multipleChoice: Boolean = false,
    val options: List<PollOption> = emptyList(),
    val totalVotes: Int = 0,
    val hasVoted: Boolean = false,
    val votedOptionIds: List<String> = emptyList(),
    val createdAt: String? = null,
)
