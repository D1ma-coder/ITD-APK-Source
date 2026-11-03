package com.itd.app.ui.screens.createpost

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.CreatePostRequest
import com.itd.app.data.model.PostPollOptionRequest
import com.itd.app.data.model.PostPollRequest
import com.itd.app.data.model.PostSpan
import com.itd.app.data.remote.ApiException
import com.itd.app.data.remote.PostsApi
import com.itd.app.data.remote.UploadContext
import com.itd.app.data.repository.MediaRepository
import com.itd.app.data.repository.PostUpdates
import com.itd.app.halloween.HalloweenRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Upload of one attachment, which starts as soon as it is picked. */
sealed interface UploadState {
    data object Pending : UploadState
    data object Processing : UploadState
    data class Uploading(val progress: Float) : UploadState
    data class Success(val serverId: String, val url: String) : UploadState
    data class Error(val message: String) : UploadState
}

data class PendingMedia(val localId: String, val uri: Uri, val state: UploadState = UploadState.Pending)

data class CreatePostState(
    val photos: List<PendingMedia> = emptyList(),
    val video: PendingMedia? = null,
    val pollEnabled: Boolean = false,
    val pollQuestion: String = "",
    val pollMultipleChoice: Boolean = false,
    val nightEnabled: Boolean = false,
    val nightText: String = "",
    val nightImages: List<PendingMedia> = emptyList(),
    val isSending: Boolean = false,
    /** Shown as the "Ошибка" dialog. */
    val error: String? = null,
    /** Shown as the hint above the publish button. */
    val hint: String? = null,
    val createdPostId: String? = null,
    val resultMessage: String? = null,
) {
    /** A video is being prepared or uploaded: publishing waits for it. */
    val videoBusy: Boolean
        get() = video?.state.let { it is UploadState.Pending || it is UploadState.Uploading || it is UploadState.Processing }
}

class CreatePostViewModel(
    private val api: PostsApi,
    private val mediaRepository: MediaRepository,
    private val wallRecipientId: String? = null,
    private val halloween: HalloweenRepository? = null,
    private val onPhoneVerification: () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(CreatePostState())
    val state = _state.asStateFlow()
    private val uploads = HashMap<String, Job>()

    /** Picked photos and videos: one video replaces nothing and excludes photos, photos are capped at 10. */
    fun attach(uris: List<Uri>, isVideo: (Uri) -> Boolean, night: Boolean = false) {
        if (uris.isEmpty()) return
        if (night) {
            uris.filterNot(isVideo).take(4 - _state.value.nightImages.size).forEach { uri ->
                val item = PendingMedia(UUID.randomUUID().toString(), uri)
                _state.update { it.copy(nightImages = it.nightImages + item) }
                upload(item, night = true)
            }
            return
        }
        val video = uris.firstOrNull(isVideo)
        if (video != null && _state.value.video == null) {
            val item = PendingMedia(UUID.randomUUID().toString(), video)
            _state.update { it.copy(video = item) }
            upload(item, video = true)
            return
        }
        val known = _state.value.photos.map { it.uri }.toSet()
        uris.filter { it !in known && !isVideo(it) }.take(10 - _state.value.photos.size).forEach { uri ->
            val item = PendingMedia(UUID.randomUUID().toString(), uri)
            _state.update { it.copy(photos = it.photos + item) }
            upload(item)
        }
    }

    /** A drawing from the "Рисунок" editor: saved as PNG and uploaded as it is. */
    fun attachDrawing(file: File, png: ByteArray, night: Boolean) {
        val item = PendingMedia(UUID.randomUUID().toString(), Uri.fromFile(file))
        if (night) {
            if (_state.value.nightImages.size >= 4) return
            _state.update { it.copy(nightImages = it.nightImages + item) }
        } else {
            if (_state.value.photos.size >= 10) return
            _state.update { it.copy(photos = it.photos + item) }
        }
        drawings[item.localId] = png
        upload(item, night = night)
    }

    private val drawings = HashMap<String, ByteArray>()

    private fun upload(item: PendingMedia, video: Boolean = false, night: Boolean = false) {
        uploads[item.localId]?.cancel()
        uploads[item.localId] = viewModelScope.launch {
            try {
                setState(item.localId, if (video) UploadState.Processing else UploadState.Uploading(0f))
                val progress = { sent: Long, total: Long? ->
                    if (total != null && total > 0) setState(item.localId, UploadState.Uploading((sent.toFloat() / total).coerceIn(0f, 1f)))
                }
                val drawing = drawings[item.localId]
                val file = if (drawing != null) {
                    mediaRepository.upload("drawing_${item.localId}.png", drawing, "image/png", UploadContext.POST, progress)
                } else {
                    mediaRepository.upload(item.uri, UploadContext.POST, onProgress = progress)
                }
                setState(item.localId, UploadState.Success(file.id, file.url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState(item.localId, UploadState.Error(e.message ?: "Не удалось загрузить"))
            } finally {
                uploads.remove(item.localId)
            }
        }
    }

    private fun setState(localId: String, state: UploadState) = _state.update { current ->
        current.copy(
            photos = current.photos.map { if (it.localId == localId) it.copy(state = state) else it },
            video = current.video?.let { if (it.localId == localId) it.copy(state = state) else it },
            nightImages = current.nightImages.map { if (it.localId == localId) it.copy(state = state) else it },
        )
    }

    fun retry(item: PendingMedia) = upload(item, video = _state.value.video?.localId == item.localId, night = _state.value.nightImages.any { it.localId == item.localId })

    fun remove(item: PendingMedia) {
        uploads.remove(item.localId)?.cancel()
        _state.update { current ->
            current.copy(
                photos = current.photos.filterNot { it.localId == item.localId },
                video = current.video?.takeIf { it.localId != item.localId },
                nightImages = current.nightImages.filterNot { it.localId == item.localId },
            )
        }
    }

    fun setPollEnabled(enabled: Boolean) = _state.update { it.copy(pollEnabled = enabled) }

    fun onPollQuestionChange(value: String) = _state.update { it.copy(pollQuestion = value) }

    fun setPollMultipleChoice(value: Boolean) = _state.update { it.copy(pollMultipleChoice = value) }

    fun setNightEnabled(enabled: Boolean) = _state.update { it.copy(nightEnabled = enabled) }

    fun onNightTextChange(value: String) = _state.update { it.copy(nightText = value, hint = null) }

    fun showHint(message: String) = _state.update { it.copy(hint = message) }

    fun dismissHint() = _state.update { it.copy(hint = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun consumeResult() = _state.update { it.copy(resultMessage = null) }

    /** Why publishing is not possible right now, or null; the publish button shows it instead of posting. */
    fun validation(text: String): String? {
        val state = _state.value
        if (!state.nightEnabled) return null
        return when {
            text.isBlank() -> "Сначала напиши дневную версию: ночная появится поверх неё"
            state.nightImages.any { it.state !is UploadState.Success && it.state !is UploadState.Error } -> "Подожди, ночные картинки ещё загружаются"
            state.nightText.isBlank() -> "Добавь текст ночной версии"
            else -> null
        }
    }

    fun submit(text: String, spans: List<PostSpan>, pollOptions: List<String>) {
        val state = _state.value
        if (state.isSending) return
        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            try {
                var attachments = state.photos.mapNotNull { (it.state as? UploadState.Success)?.serverId }
                (state.video?.state as? UploadState.Success)?.serverId?.let { attachments = listOf(it) }
                val poll = if (state.pollEnabled) {
                    PostPollRequest(state.pollQuestion, pollOptions.filter { it.isNotBlank() }.map(::PostPollOptionRequest), state.pollMultipleChoice)
                } else {
                    null
                }
                val post = api.create(CreatePostRequest(text, attachments, spans = spans, poll = poll, wallRecipientId = wallRecipientId))
                var message: String? = null
                if (state.nightEnabled && halloween != null) {
                    message = try {
                        halloween.createWerewolf(post.id, state.nightText.trim(), ArrayList(state.nightImages.mapNotNull { (it.state as? UploadState.Success)?.url }))
                        "Пост стал оборотнем"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        "Пост опубликован, но оборотнем не стал"
                    }
                }
                PostUpdates.create(post)
                _state.update { it.copy(isSending = false, createdPostId = post.id, resultMessage = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.code == "PHONE_VERIFICATION_REQUIRED") {
                    onPhoneVerification()
                    _state.update { it.copy(isSending = false) }
                } else {
                    _state.update { it.copy(isSending = false, error = e.message) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSending = false) }
            }
        }
    }
}
