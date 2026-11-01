package com.itd.app.ui.screens.hashtag

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.itd.app.data.analytics.ImpressionSource
import com.itd.app.data.repository.PostStatsPoller
import com.itd.app.data.repository.PostUpdates
import com.itd.app.ui.common.TrackPostImpressions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itd.app.data.model.Post
import com.itd.app.di.AppContainer
import com.itd.app.ui.components.PostCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun HashtagScreen(container: AppContainer, name: String, onPost: (com.itd.app.data.model.Post) -> Unit, onProfile: (String) -> Unit) {
    var posts by remember(name) { mutableStateOf<List<Post>>(emptyList()) }
    var cursor by remember(name) { mutableStateOf<String?>(null) }
    var hasMore by remember(name) { mutableStateOf(true) }
    var error by remember(name) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        try {
            val data = container.searchApi.hashtagPosts(name, cursor = cursor).data
            posts = (posts + data.posts).distinctBy { it.id }
            cursor = data.pagination.nextCursor; hasMore = data.pagination.hasMore; error = null
        } catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) { error = failure.message }
    }
    LaunchedEffect(name) { load() }
    LaunchedEffect(name) { PostUpdates.updates.collect { update -> posts = posts.map { PostUpdates.apply(it, update) } } }
    LaunchedEffect(name) { PostUpdates.removed.collect { id -> posts = posts.filterNot { it.id == id } } }
    val listState = rememberLazyListState()
    TrackPostImpressions(listState, posts, ImpressionSource.HASHTAG, name, container.analytics.dwell, PostStatsPoller.get(container.postsApi))
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item { Text("#$name", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall) }
        items(posts, key = { it.id }) { post -> PostCard(post, { onPost(post) }, { onProfile(post.author.username ?: post.author.id) }, onOpenOriginal = onPost) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
        if (hasMore) item { TextButton(onClick = { scope.launch { load() } }, modifier = Modifier.fillMaxWidth()) { Text("Загрузить ещё") } }
    }
}
