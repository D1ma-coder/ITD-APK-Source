package com.itd.app.ui.screens.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.itd.app.data.analytics.DwellTracker
import com.itd.app.data.analytics.ImpressionSource
import com.itd.app.data.repository.PostStatsPoller
import com.itd.app.ui.common.TrackPostImpressions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itd.app.ui.common.ErrorContent
import com.itd.app.ui.common.LoadingContent
import com.itd.app.ui.components.PostCard

@Composable
fun FeedScreen(viewModel: FeedViewModel, dwell: DwellTracker, stats: PostStatsPoller?, onPost: (com.itd.app.data.model.Post) -> Unit, onProfile: (String) -> Unit, onHashtag: (String) -> Unit, onSearch: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val source = when (state.selectedTab) {
        FeedTab.Popular -> ImpressionSource.FEED_GLOBAL
        FeedTab.Following -> ImpressionSource.FEED_FOLLOWING
        FeedTab.Clan -> ImpressionSource.FEED_CLAN
    }
    TrackPostImpressions(listState, state.posts, source, "", dwell, stats)
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.selectedTab.ordinal, modifier = Modifier.statusBarsPadding()) {
            FeedTab.entries.forEach { tab -> Tab(state.selectedTab == tab, { viewModel.selectTab(tab) }, text = { Text(tab.title) }) }
            Tab(false, onSearch, text = { Text("Поиск") })
        }
        when {
            state.isLoading && state.posts.isEmpty() -> LoadingContent()
            state.error != null && state.posts.isEmpty() -> ErrorContent(state.error!!, viewModel::refresh)
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                item { com.itd.app.halloween.HalloweenFeedBanner() }
                if (!state.isLoading && state.error == null && state.posts.isEmpty()) {
                    item {
                        when (state.selectedTab) {
                            FeedTab.Popular -> com.itd.app.ui.common.EmptyState("Тут пока пусто", "Попробуй обновить ленту")
                            FeedTab.Following -> com.itd.app.ui.common.EmptyState("Подпишись на кого-нибудь,\nчтобы видеть их посты")
                            FeedTab.Clan -> com.itd.app.ui.common.EmptyState("Вступи в клан,\nчтобы видеть посты участников")
                        }
                    }
                }
                items(state.posts, key = { it.id }) { post ->
                    PostCard(post, { onPost(post) }, { onProfile(post.author.username ?: post.author.id) }, onHashtag, onOpenOriginal = onPost)
                }
                if (state.hasMore) item {
                    LaunchedEffect(state.nextCursor) { viewModel.loadMore() }
                    Box(Modifier.fillMaxWidth().padding(16.dp)) { if (state.isLoadingMore) CircularProgressIndicator() }
                }
                state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            }
        }
    }
}
