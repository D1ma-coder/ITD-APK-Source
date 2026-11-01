package com.itd.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.itd.app.data.model.EditPostRequest
import com.itd.app.data.model.Post
import com.itd.app.data.model.ReportRequest
import com.itd.app.data.remote.ApiException
import com.itd.app.data.remote.PostsApi
import com.itd.app.data.repository.PostUpdate
import com.itd.app.data.repository.PostUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Post actions shared by every list; results are broadcast through [PostUpdates]. */
class PostInteractor(private val api: PostsApi, private val halloween: com.itd.app.halloween.HalloweenRepository? = null) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val likesInFlight = HashSet<String>()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _phoneVerification = MutableStateFlow(false)

    /** Set when the server requires phone confirmation before posting. */
    val phoneVerification: StateFlow<Boolean> = _phoneVerification.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun requirePhoneVerification() {
        _phoneVerification.value = true
    }

    fun dismissPhoneVerification() {
        _phoneVerification.value = false
    }

    fun handleError(error: Exception) {
        if (error is ApiException && error.code == "PHONE_VERIFICATION_REQUIRED") {
            _phoneVerification.value = true
        } else {
            _message.value = error.message ?: "Произошла ошибка"
        }
    }

    fun toggleLike(post: Post, nightWerewolf: Boolean = false) {
        if (!likesInFlight.add(post.id)) return
        val liked = !post.isLiked
        PostUpdates.emit(PostUpdate(post.id, isLiked = liked, likesCount = (post.likesCount + if (liked) 1 else -1).coerceAtLeast(0)))
        scope.launch {
            try {
                if (liked) {
                    val response = api.like(post.id)
                    PostUpdates.emit(PostUpdate(post.id, isLiked = response.liked, likesCount = response.likesCount))
                    if (nightWerewolf) collectNightCandy(post.id)
                } else {
                    api.unlike(post.id)
                }
            } catch (e: Exception) {
                PostUpdates.emit(PostUpdate(post.id, isLiked = post.isLiked, likesCount = post.likesCount))
                handleError(e)
            } finally {
                likesInFlight.remove(post.id)
            }
        }
    }

    private suspend fun collectNightCandy(postId: String) {
        val candy = try {
            halloween?.reactToWerewolf(postId)
        } catch (_: Exception) {
            null
        } ?: return
        com.itd.app.halloween.HalloweenSounds.play(com.itd.app.halloween.HalloweenSound.HOWL, forced = true)
        com.itd.app.halloween.HalloweenToasts.show(
            "Ночная конфета: ${candy.name}",
            if (candy.count > 0) "В банке ${candy.count}" else null,
            candy.kind,
        )
    }

    fun repost(post: Post, comment: String?, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                val created = api.repost(post.id, comment?.takeIf { it.isNotBlank() })
                PostUpdates.emit(PostUpdate(post.id, isReposted = true, repostsCount = post.repostsCount + 1))
                PostUpdates.create(created)
                onDone()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun delete(post: Post) {
        scope.launch {
            try {
                api.delete(post.id)
                PostUpdates.remove(post.id)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun togglePin(post: Post, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                if (post.isPinned) api.unpin(post.id) else api.pin(post.id)
                onDone()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun edit(post: Post, content: String, onDone: () -> Unit) {
        scope.launch {
            try {
                api.edit(post.id, EditPostRequest(content))
                PostUpdates.emit(PostUpdate(post.id, content = content, spans = emptyList(), editedAt = java.time.Instant.now().toString()))
                onDone()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun report(targetType: String, targetId: String, reason: String, description: String? = null) {
        scope.launch {
            try {
                api.report(ReportRequest(targetType, targetId, reason, description))
                _message.value = "Жалоба отправлена"
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun vote(post: Post, optionIds: List<String>) {
        if (optionIds.isEmpty()) return
        scope.launch {
            try {
                PostUpdates.emit(PostUpdate(post.id, poll = api.vote(post.id, optionIds).data))
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    companion object {
        fun postUrl(post: Post) = "https://итд.com/@${post.author.username.orEmpty()}/post/${post.id}"

        /** Opens the verification bot in Telegram, falling back to the web link. */
        fun openVerificationBot(context: Context, userId: String) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=itd_verification_bot&start=$userId")))
            } catch (_: Exception) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/itd_verification_bot?start=$userId")))
            }
        }
    }
}
