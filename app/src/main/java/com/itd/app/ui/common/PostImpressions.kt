package com.itd.app.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.itd.app.data.analytics.DwellTracker
import com.itd.app.data.analytics.HiddenReason
import com.itd.app.data.analytics.ImpressionSource
import com.itd.app.data.model.Post
import com.itd.app.data.repository.PostStatsPoller

/** Posts whose list item is at least [threshold] visible, keyed by post id (items must use the post id as key). */
fun LazyListState.visiblePosts(posts: List<Post>, threshold: Float = 0.5f): HashMap<String, Post> {
    val byId = posts.associateBy { it.id }
    val info = layoutInfo
    val result = HashMap<String, Post>(info.visibleItemsInfo.size)
    for (item in info.visibleItemsInfo) {
        val id = item.key as? String ?: continue
        val post = byId[id] ?: continue
        val visible = (minOf(item.offset + item.size, info.viewportEndOffset) - maxOf(item.offset, info.viewportStartOffset)).coerceAtLeast(0)
        if ((if (item.size > 0) visible.toFloat() / item.size else 0f) >= threshold) result[id] = post
    }
    return result
}

/** Id of the first video post that is at least 30% visible; it autoplays. */
fun LazyListState.autoplayVideoPostId(posts: List<Post>): String? {
    val videoIds = posts.filter { post -> post.attachments.firstOrNull()?.type == "video" }.map { it.id }.toSet()
    if (videoIds.isEmpty()) return null
    val info = layoutInfo
    return info.visibleItemsInfo.firstOrNull { item ->
        val id = item.key as? String
        if (id == null || id !in videoIds) return@firstOrNull false
        val visible = (minOf(item.offset + item.size, info.viewportEndOffset) - maxOf(item.offset, info.viewportStartOffset)).coerceAtLeast(0)
        (if (item.size > 0) visible.toFloat() / item.size else 0f) >= 0.3f
    }?.key as? String
}

/**
 * Starts dwell timers for posts entering the viewport, stops them for posts leaving it,
 * and keeps the live stats poller in sync with what is on screen (reposted originals included).
 */
@Composable
fun TrackPostImpressions(
    listState: LazyListState,
    posts: List<Post>,
    source: ImpressionSource,
    sourceContext: String,
    dwell: DwellTracker,
    stats: PostStatsPoller?,
) {
    val currentPosts by rememberUpdatedState(posts)
    LaunchedEffect(listState, source, sourceContext) {
        var shown = emptySet<String>()
        var watched = emptySet<String>()
        snapshotFlow { listState.visiblePosts(currentPosts) }.collect { visible ->
            val ids = HashSet<String>(visible.size * 2)
            for ((id, post) in visible) {
                ids.add(id)
                post.originalPost?.id?.let(ids::add)
            }
            for ((id, post) in visible) if (id !in shown) dwell.start(id, post.vs, source, sourceContext)
            for (id in shown) if (!visible.containsKey(id)) dwell.stop(id, HiddenReason.NOT_IN_VIEWPORT)
            for (id in ids) if (id !in watched) stats?.onVisible(id)
            for (id in watched) if (id !in ids) stats?.onHidden(id)
            shown = visible.keys.toSet()
            watched = ids
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { dwell.onUserActivity() }
    }
    DisposableEffect(listState, source, sourceContext) {
        onDispose { dwell.stopAll(HiddenReason.COMPONENT_UNMOUNTED, sync = false) }
    }
}
