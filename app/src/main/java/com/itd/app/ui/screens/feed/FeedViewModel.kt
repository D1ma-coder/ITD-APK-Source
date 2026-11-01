package com.itd.app.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.remote.PostsApi
import com.itd.app.data.repository.PostUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedViewModel(private val postsApi: PostsApi) : ViewModel() {
    private val _state = MutableStateFlow(FeedUiState())
    val state = _state.asStateFlow()
    private var loadingJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            PostUpdates.updates.collect { update -> _state.update { it.copy(posts = it.posts.map { post -> PostUpdates.apply(post, update) }) } }
        }
        viewModelScope.launch {
            PostUpdates.removed.collect { id -> _state.update { it.copy(posts = it.posts.filterNot { post -> post.id == id || post.originalPost?.id == id }) } }
        }
        viewModelScope.launch {
            PostUpdates.created.collect { post ->
                if (_state.value.selectedTab != FeedTab.Clan) _state.update { it.copy(posts = (listOf(post) + it.posts).distinctBy { p -> p.id }) }
            }
        }
    }

    fun selectTab(tab: FeedTab) {
        if (tab == _state.value.selectedTab) return
        loadingJob?.cancel()
        _state.value = FeedUiState(selectedTab = tab)
        refresh()
    }

    fun refresh() = load(true)
    fun loadMore() = load(false)

    private fun load(refresh: Boolean) {
        val current = _state.value
        if (!refresh && (current.isLoading || current.isLoadingMore || !current.hasMore)) return
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = refresh, isLoadingMore = !refresh, error = null) }
            try {
                val response = postsApi.feed(current.selectedTab.value, cursor = if (refresh) null else current.nextCursor).data
                _state.update { it.copy(
                    posts = (if (refresh) response.posts else it.posts + response.posts).distinctBy { post -> post.id },
                    isLoading = false, isLoadingMore = false, nextCursor = response.pagination.nextCursor,
                    hasMore = response.pagination.hasMore,
                ) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(isLoading = false, isLoadingMore = false, error = error.message ?: "Не удалось загрузить ленту") } }
        }
    }

    private val votingIds = mutableSetOf<String>()
    fun vote(id: String, optionIds: List<String>) {
        if (optionIds.isEmpty() || !votingIds.add(id)) return
        viewModelScope.launch {
            try {
                val poll = postsApi.vote(id, optionIds).data
                _state.update { it.copy(posts = it.posts.map { post -> if (post.id == id) post.copy(poll = poll) else post }) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(error = error.message) } }
            finally { votingIds.remove(id) }
        }
    }

    fun toggleLike(id: String) {
        val post = _state.value.posts.find { it.id == id } ?: return
        if (id in _state.value.updatingPostIds) return
        _state.update { it.copy(updatingPostIds = it.updatingPostIds + id,
            posts = it.posts.map { p -> if (p.id == id) p.copy(isLiked = !post.isLiked, likesCount = (post.likesCount + if (post.isLiked) -1 else 1).coerceAtLeast(0)) else p }) }
        viewModelScope.launch {
            try {
                if (post.isLiked) postsApi.unlike(id)
                else postsApi.like(id).also { response -> _state.update { it.copy(posts = it.posts.map { p -> if (p.id == id) p.copy(isLiked = response.liked, likesCount = response.likesCount) else p }) } }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _state.update { it.copy(posts = it.posts.map { p -> if (p.id == id) post else p }, error = error.message) } }
            finally { _state.update { it.copy(updatingPostIds = it.updatingPostIds - id) } }
        }
    }
}
