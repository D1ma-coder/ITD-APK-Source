package com.itd.app.ui.screens.feed

import com.itd.app.data.model.Post

enum class FeedTab(val value: String, val title: String) {
    Popular("popular", "Для вас"), Following("following", "Подписки"), Clan("clan", "Лента кланов")
}

data class FeedUiState(
    val posts: List<Post> = emptyList(),
    val selectedTab: FeedTab = FeedTab.Popular,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean = true,
    val updatingPostIds: Set<String> = emptySet(),
)
