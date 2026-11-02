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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.itd.app.R
import com.itd.app.ui.components.PillTabs
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
    val colors = MaterialTheme.colorScheme
    var headerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().background(colors.background)) {
        when {
            state.isLoading && state.posts.isEmpty() -> LoadingContent()
            state.error != null && state.posts.isEmpty() -> ErrorContent(state.error!!, viewModel::refresh)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = headerHeight, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp),
            ) {
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
        Row(
            Modifier.fillMaxWidth().zIndex(1f).statusBarsPadding().padding(start = 12.dp, top = 8.dp, end = 12.dp)
                .height(IntrinsicSize.Min)
                .onGloballyPositioned { headerHeight = with(density) { (it.positionInParent().y + it.size.height).toDp() } + 8.dp },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillTabs(FeedTab.entries.map { it.title }, state.selectedTab.ordinal, { viewModel.selectTab(FeedTab.entries[it]) }, Modifier.weight(1f))
            val track = if (isSystemInDarkTheme()) colors.background.lighten(0.08f) else colors.background.lighten(-0.08f)
            Box(
                Modifier.fillMaxHeight().aspectRatio(1f).clip(CircleShape).background(track.copy(alpha = 0.92f)).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_search), "Поиск", Modifier.size(16.dp), tint = colors.onSurface)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.Color.lighten(delta: Float) = androidx.compose.ui.graphics.Color(
    (red + delta).coerceIn(0f, 1f),
    (green + delta).coerceIn(0f, 1f),
    (blue + delta).coerceIn(0f, 1f),
    alpha,
)
