package com.itd.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.Post
import com.itd.app.data.model.ReportRequest
import com.itd.app.data.model.UserProfile
import com.itd.app.data.remote.PostsApi
import com.itd.app.data.remote.UsersApi
import com.itd.app.data.repository.PostUpdates
import com.itd.app.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileTab(val title: String) { Posts("Посты"), Liked("Понравилось") }

data class ProfilePosts(
    val posts: List<Post> = emptyList(),
    val cursor: String? = null,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val loaded: Boolean = false,
)

data class ProfileContent(
    val profile: UserProfile,
    val tab: ProfileTab = ProfileTab.Posts,
    val tabs: Map<ProfileTab, ProfilePosts> = emptyMap(),
    val followBusy: Boolean = false,
) {
    val current: ProfilePosts get() = tabs[tab] ?: ProfilePosts()
    val posts: List<Post> get() = current.posts
}

class ProfileViewModel(private val api: UsersApi, private val postsApi: PostsApi, private val username: String) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<ProfileContent>>(LoadState.Loading)
    val state = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            PostUpdates.updates.collect { update ->
                updateContent { content ->
                    content.copy(tabs = content.tabs.mapValues { (_, page) -> page.copy(posts = page.posts.map { PostUpdates.apply(it, update) }) })
                }
            }
        }
    }

    private fun updateContent(transform: (ProfileContent) -> ProfileContent) {
        _state.update { state -> if (state is LoadState.Ready) LoadState.Ready(transform(state.data)) else state }
    }

    private val content: ProfileContent? get() = (_state.value as? LoadState.Ready)?.data

    init {
        viewModelScope.launch {
            PostUpdates.removed.collect { id ->
                updateContent { content ->
                    content.copy(tabs = content.tabs.mapValues { (_, page) -> page.copy(posts = page.posts.filterNot { it.id == id }) })
                }
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun refresh() = viewModelScope.launch {
        try {
            val profile = api.profile(username)
            val tab = content?.tab ?: ProfileTab.Posts
            _state.value = LoadState.Ready(ProfileContent(profile, tab))
            loadPage(tab, reset = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (content == null) _state.value = LoadState.Error(e.message ?: "Не удалось загрузить")
        }
    }

    fun selectTab(tab: ProfileTab) {
        val current = content ?: return
        if (current.tab == tab) return
        updateContent { it.copy(tab = tab) }
        if (current.tabs[tab]?.loaded != true) loadPage(tab, reset = true)
    }

    fun loadMore() {
        val current = content ?: return
        val page = current.current
        if (page.loading || !page.hasMore) return
        loadPage(current.tab, reset = false)
    }

    private fun loadPage(tab: ProfileTab, reset: Boolean) {
        val current = content ?: return
        val page = current.tabs[tab] ?: ProfilePosts()
        updateContent { it.copy(tabs = it.tabs + (tab to page.copy(loading = true))) }
        viewModelScope.launch {
            try {
                val cursor = if (reset) null else page.cursor
                val response = when (tab) {
                    ProfileTab.Posts -> api.posts(current.profile.id, cursor = cursor, pinnedPostId = current.profile.pinnedPostId)
                    ProfileTab.Liked -> api.likedPosts(current.profile.id, cursor = cursor)
                }.data
                updateContent {
                    val old = if (reset) emptyList() else (it.tabs[tab]?.posts ?: emptyList())
                    it.copy(
                        tabs = it.tabs + (
                            tab to ProfilePosts(
                                posts = (old + response.posts).distinctBy { post -> post.id },
                                cursor = response.pagination.nextCursor,
                                hasMore = response.pagination.hasMore,
                                loading = false,
                                loaded = true,
                            )
                            ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateContent { it.copy(tabs = it.tabs + (tab to page.copy(loading = false, loaded = true, hasMore = false))) }
            }
        }
    }

    fun toggleFollow() {
        val current = content ?: return
        if (current.followBusy) return
        val profile = current.profile
        val following = profile.isFollowing
        updateContent {
            it.copy(
                followBusy = true,
                profile = it.profile.copy(
                    isFollowing = !following,
                    followersCount = (it.profile.followersCount + if (following) -1 else 1).coerceAtLeast(0),
                ),
            )
        }
        viewModelScope.launch {
            try {
                if (following) api.unfollow(profile.id) else api.follow(profile.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateContent { it.copy(profile = profile) }
                _message.value = e.message ?: "Произошла ошибка"
            } finally {
                updateContent { it.copy(followBusy = false) }
            }
        }
    }

    fun toggleBlock() {
        val profile = content?.profile ?: return
        viewModelScope.launch {
            try {
                if (profile.isBlockedByMe) api.unblock(profile.id) else api.block(profile.id)
                updateContent { it.copy(profile = it.profile.copy(isBlockedByMe = !profile.isBlockedByMe, isFollowing = false)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "Произошла ошибка"
            }
        }
    }

    fun report(reason: String) {
        val profile = content?.profile ?: return
        viewModelScope.launch {
            try {
                postsApi.report(ReportRequest("user", profile.id, reason))
                _message.value = "Жалоба отправлена"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "Произошла ошибка"
            }
        }
    }

    fun toggleLike(id: String) {
        val post = content?.tabs?.values?.flatMap { it.posts }?.firstOrNull { it.id == id } ?: return
        val liked = !post.isLiked
        PostUpdates.emit(
            com.itd.app.data.repository.PostUpdate(id, isLiked = liked, likesCount = (post.likesCount + if (liked) 1 else -1).coerceAtLeast(0)),
        )
        viewModelScope.launch {
            try {
                if (liked) {
                    val response = postsApi.like(id)
                    PostUpdates.emit(com.itd.app.data.repository.PostUpdate(id, isLiked = response.liked, likesCount = response.likesCount))
                } else {
                    postsApi.unlike(id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PostUpdates.emit(com.itd.app.data.repository.PostUpdate(id, isLiked = post.isLiked, likesCount = post.likesCount))
            }
        }
    }
}
