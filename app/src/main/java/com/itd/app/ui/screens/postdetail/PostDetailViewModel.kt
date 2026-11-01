package com.itd.app.ui.screens.postdetail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.Comment
import com.itd.app.data.model.Post
import com.itd.app.data.remote.ApiException
import com.itd.app.data.remote.PostsApi
import com.itd.app.data.remote.UploadContext
import com.itd.app.data.repository.MediaRepository
import com.itd.app.data.repository.PostUpdate
import com.itd.app.data.repository.PostUpdates
import com.itd.app.ui.common.LoadState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CommentSort(val api: String, val title: String) {
    Popular("popular", "Популярные"),
    Oldest("oldest", "Сначала старые"),
    Newest("newest", "Сначала новые"),
}

data class CommentThread(
    val comment: Comment,
    val replies: List<Comment> = comment.replies,
    val repliesPage: Int = 1,
    val loadingReplies: Boolean = false,
) {
    val hiddenReplies: Int get() = (comment.repliesCount - replies.size).coerceAtLeast(0)
}

data class PendingAttachment(val uri: Uri, val isAudio: Boolean, val uploadedId: String? = null, val failed: Boolean = false)

data class PostDetailContent(
    val post: Post,
    val threads: List<CommentThread> = emptyList(),
    val cursor: String? = null,
    val hasMore: Boolean = false,
    val commentsLoaded: Boolean = false,
    val commentsOffline: Boolean = false,
    val sort: CommentSort = CommentSort.Popular,
    val replyTo: Comment? = null,
    val attachments: List<PendingAttachment> = emptyList(),
    val sending: Boolean = false,
) {
    val comments: List<Comment> get() = threads.map { it.comment }
}

class PostDetailViewModel(
    private val api: PostsApi,
    private val postId: String,
    private val media: MediaRepository? = null,
    private val onPhoneVerification: () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<PostDetailContent>>(LoadState.Loading)
    val state = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var loadingComments = false

    init {
        refresh()
        viewModelScope.launch {
            PostUpdates.updates.collect { update -> updateContent { it.copy(post = PostUpdates.apply(it.post, update)) } }
        }
    }

    private val content: PostDetailContent? get() = (_state.value as? LoadState.Ready)?.data

    private fun updateContent(transform: (PostDetailContent) -> PostDetailContent) {
        _state.update { state -> if (state is LoadState.Ready) LoadState.Ready(transform(state.data)) else state }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun refresh() = viewModelScope.launch {
        try {
            val post = api.post(postId).data
            _state.value = LoadState.Ready(content?.copy(post = post) ?: PostDetailContent(post))
            loadComments(reset = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (content == null) _state.value = LoadState.Error("Не удалось загрузить пост")
        }
    }

    fun setSort(sort: CommentSort) {
        if (content?.sort == sort) return
        updateContent { it.copy(sort = sort, threads = emptyList(), commentsLoaded = false) }
        loadComments(reset = true)
    }

    fun loadMoreComments() {
        val current = content ?: return
        if (!current.hasMore) return
        loadComments(reset = false)
    }

    fun retryComments() = loadComments(reset = true)

    private fun loadComments(reset: Boolean) {
        val current = content ?: return
        if (loadingComments) return
        loadingComments = true
        viewModelScope.launch {
            try {
                val response = api.comments(postId, sort = current.sort.api, cursor = if (reset) null else current.cursor).data
                updateContent {
                    val old = if (reset) emptyList() else it.threads
                    it.copy(
                        threads = (old + response.comments.map(::CommentThread)).distinctBy { thread -> thread.comment.id },
                        cursor = response.nextCursor,
                        hasMore = response.hasMore,
                        commentsLoaded = true,
                        commentsOffline = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                updateContent { it.copy(commentsOffline = true, commentsLoaded = true) }
            } catch (e: Exception) {
                updateContent { it.copy(commentsLoaded = true) }
                _message.value = e.message
            } finally {
                loadingComments = false
            }
        }
    }

    fun loadReplies(commentId: String) {
        val thread = content?.threads?.firstOrNull { it.comment.id == commentId } ?: return
        if (thread.loadingReplies) return
        updateThread(commentId) { it.copy(loadingReplies = true) }
        viewModelScope.launch {
            try {
                val page = if (thread.replies.isEmpty()) 1 else thread.repliesPage + 1
                val response = api.replies(commentId, page = page).data
                updateThread(commentId) {
                    it.copy(replies = (it.replies + response.replies).distinctBy { reply -> reply.id }, repliesPage = page, loadingReplies = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateThread(commentId) { it.copy(loadingReplies = false) }
            }
        }
    }

    private fun updateThread(commentId: String, transform: (CommentThread) -> CommentThread) {
        updateContent { content -> content.copy(threads = content.threads.map { if (it.comment.id == commentId) transform(it) else it }) }
    }

    fun replyTo(comment: Comment?) = updateContent { it.copy(replyTo = comment) }

    fun addAttachments(uris: List<Uri>, audio: Boolean) {
        val repository = media ?: return
        uris.forEach { uri ->
            updateContent { it.copy(attachments = it.attachments + PendingAttachment(uri, audio)) }
            viewModelScope.launch {
                try {
                    val uploaded = repository.upload(uri, UploadContext.COMMENT) { _, _ -> }
                    updateContent { content -> content.copy(attachments = content.attachments.map { if (it.uri == uri) it.copy(uploadedId = uploaded.id) else it }) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    updateContent { content -> content.copy(attachments = content.attachments.map { if (it.uri == uri) it.copy(failed = true) else it }) }
                }
            }
        }
    }

    fun removeAttachment(uri: Uri) = updateContent { it.copy(attachments = it.attachments.filterNot { attachment -> attachment.uri == uri }) }

    fun send(text: String, onSent: () -> Unit) {
        val current = content ?: return
        val ids = current.attachments.mapNotNull { it.uploadedId }
        if ((text.isBlank() && ids.isEmpty()) || current.sending || current.attachments.any { it.uploadedId == null && !it.failed }) return
        updateContent { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                val target = current.replyTo
                if (target == null) {
                    val comment = api.comment(postId, text, ids)
                    updateContent { it.copy(threads = listOf(CommentThread(comment)) + it.threads) }
                } else {
                    val parentId = current.threads.firstOrNull { thread -> thread.comment.id == target.id || thread.replies.any { it.id == target.id } }?.comment?.id ?: target.id
                    val reply = api.reply(parentId, text, target.author.id, ids)
                    updateThread(parentId) { it.copy(replies = it.replies + reply, comment = it.comment.copy(repliesCount = it.comment.repliesCount + 1)) }
                }
                PostUpdates.emit(PostUpdate(postId, commentsCount = current.post.commentsCount + 1))
                updateContent { it.copy(replyTo = null, attachments = emptyList()) }
                onSent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.code == "PHONE_VERIFICATION_REQUIRED") onPhoneVerification() else _message.value = e.message
            } catch (e: Exception) {
                _message.value = e.message ?: "Произошла ошибка"
            } finally {
                updateContent { it.copy(sending = false) }
            }
        }
    }

    fun deleteComment(comment: Comment) {
        viewModelScope.launch {
            try {
                api.deleteComment(comment.id)
                updateContent { content ->
                    content.copy(
                        threads = content.threads.filterNot { it.comment.id == comment.id }
                            .map { it.copy(replies = it.replies.filterNot { reply -> reply.id == comment.id }) },
                    )
                }
                PostUpdates.emit(PostUpdate(postId, commentsCount = ((content?.post?.commentsCount ?: 1) - 1).coerceAtLeast(0)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "Произошла ошибка"
            }
        }
    }

    fun toggleCommentLike(comment: Comment) {
        val liked = !comment.isLiked
        fun apply(target: Comment, isLiked: Boolean, count: Int) = updateContent { content ->
            content.copy(threads = content.threads.map { thread ->
                thread.copy(
                    comment = if (thread.comment.id == target.id) thread.comment.copy(isLiked = isLiked, likesCount = count) else thread.comment,
                    replies = thread.replies.map { if (it.id == target.id) it.copy(isLiked = isLiked, likesCount = count) else it },
                )
            })
        }
        apply(comment, liked, (comment.likesCount + if (liked) 1 else -1).coerceAtLeast(0))
        viewModelScope.launch {
            try {
                if (liked) {
                    val response = api.likeComment(comment.id)
                    apply(comment, response.liked, response.likesCount)
                } else {
                    api.unlikeComment(comment.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                apply(comment, comment.isLiked, comment.likesCount)
            }
        }
    }
}
