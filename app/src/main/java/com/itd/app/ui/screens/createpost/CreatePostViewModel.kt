package com.itd.app.ui.screens.createpost

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftAttachment(val uri: Uri, val id: String? = null, val url: String? = null, val progress: Float = 0f, val failed: Boolean = false) {
    val uploading: Boolean get() = id == null && !failed
}

data class PollDraft(val question: String = "", val options: List<String> = listOf("", ""), val multipleChoice: Boolean = false) {
    fun toRequest(): PostPollRequest? {
        val filled = options.map { it.trim() }.filter { it.isNotEmpty() }
        if (question.isBlank() || filled.size < 2) return null
        return PostPollRequest(question.trim(), filled.map(::PostPollOptionRequest), multipleChoice)
    }
}

data class CreatePostState(
    val text: TextFieldValue = TextFieldValue(""),
    val spans: List<PostSpan> = emptyList(),
    val attachments: List<DraftAttachment> = emptyList(),
    val poll: PollDraft? = null,
    val nightEnabled: Boolean = false,
    val nightText: String = "",
    val nightImages: List<DraftAttachment> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val createdPostId: String? = null,
    val resultMessage: String? = null,
) {
    val content: String get() = text.text
    val uploading: Boolean get() = attachments.any { it.uploading } || nightImages.any { it.uploading }
    val canPublish: Boolean
        get() = !isSending && !uploading && (content.isNotBlank() || attachments.any { it.id != null } || poll?.toRequest() != null)
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

    fun onTextChange(value: TextFieldValue) {
        val old = _state.value.text.text
        val new = value.text
        if (old == new) {
            _state.update { it.copy(text = value) }
            return
        }
        val prefix = old.commonPrefixWith(new).length
        val suffix = old.substring(prefix).commonSuffixWith(new.substring(prefix)).length
        val removedEnd = old.length - suffix
        val delta = new.length - old.length
        val spans = _state.value.spans.mapNotNull { span ->
            val start = span.offset
            val end = span.offset + span.length
            when {
                end <= prefix -> span
                start >= removedEnd -> span.copy(offset = start + delta)
                else -> {
                    val newStart = minOf(start, prefix)
                    val newEnd = if (end >= removedEnd) end + delta else prefix
                    if (newEnd > newStart) span.copy(offset = newStart, length = newEnd - newStart) else null
                }
            }
        }
        _state.update { it.copy(text = value, spans = spans, error = null) }
    }

    /** Applies [type] to the selection; "plain" removes all formatting from it. */
    fun format(type: String, url: String? = null) {
        val selection = _state.value.text.selection
        if (selection.collapsed) return
        val start = selection.min
        val end = selection.max
        val kept = _state.value.spans.flatMap { span ->
            val spanEnd = span.offset + span.length
            if (spanEnd <= start || span.offset >= end || (type != "plain" && span.type != type)) {
                listOf(span)
            } else {
                listOfNotNull(
                    if (span.offset < start) span.copy(length = start - span.offset) else null,
                    if (spanEnd > end) span.copy(offset = end, length = spanEnd - end) else null,
                )
            }
        }
        val added = if (type == "plain") emptyList() else listOf(PostSpan(type, end - start, start, url = url))
        _state.update { it.copy(spans = (kept + added).sortedBy { span -> span.offset }, text = it.text.copy(selection = TextRange(end))) }
    }

    fun attach(uris: List<Uri>, night: Boolean = false) {
        uris.forEach { uri ->
            val draft = DraftAttachment(uri)
            _state.update { if (night) it.copy(nightImages = it.nightImages + draft) else it.copy(attachments = it.attachments + draft) }
            viewModelScope.launch {
                try {
                    val file = mediaRepository.upload(uri, UploadContext.POST) { sent, total ->
                        if (total != null && total > 0) updateAttachment(uri, night) { it.copy(progress = (sent.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                    updateAttachment(uri, night) { it.copy(id = file.id, url = file.url, progress = 1f) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    updateAttachment(uri, night) { it.copy(failed = true) }
                    _state.update { it.copy(error = e.message ?: "Ошибка") }
                }
            }
        }
    }

    private fun updateAttachment(uri: Uri, night: Boolean, transform: (DraftAttachment) -> DraftAttachment) {
        _state.update { state ->
            if (night) {
                state.copy(nightImages = state.nightImages.map { if (it.uri == uri) transform(it) else it })
            } else {
                state.copy(attachments = state.attachments.map { if (it.uri == uri) transform(it) else it })
            }
        }
    }

    fun removeAttachment(uri: Uri, night: Boolean = false) = _state.update {
        if (night) it.copy(nightImages = it.nightImages.filterNot { a -> a.uri == uri }) else it.copy(attachments = it.attachments.filterNot { a -> a.uri == uri })
    }

    fun setPoll(poll: PollDraft?) = _state.update { it.copy(poll = poll) }

    fun setNightEnabled(enabled: Boolean) = _state.update { it.copy(nightEnabled = enabled) }

    fun onNightTextChange(value: String) = _state.update { it.copy(nightText = value, error = null) }

    fun consumeResult() = _state.update { it.copy(resultMessage = null) }

    fun submit() {
        val state = _state.value
        if (!state.canPublish) return
        if (state.nightEnabled) {
            val problem = when {
                state.content.isBlank() -> "Сначала напиши дневную версию: ночная появится поверх неё"
                state.nightImages.any { it.uploading } -> "Подожди, ночные картинки ещё загружаются"
                state.nightText.isBlank() -> "Добавь текст ночной версии"
                else -> null
            }
            if (problem != null) {
                _state.update { it.copy(error = problem) }
                return
            }
        }
        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            try {
                val post = api.create(
                    CreatePostRequest(
                        state.content,
                        state.attachments.mapNotNull { it.id },
                        spans = state.spans,
                        poll = state.poll?.toRequest(),
                        wallRecipientId = wallRecipientId,
                    ),
                )
                var message: String? = null
                if (state.nightEnabled && halloween != null) {
                    message = try {
                        halloween.createWerewolf(post.id, state.nightText.trim(), ArrayList(state.nightImages.mapNotNull { it.url }))
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
                if (e.code == "PHONE_VERIFICATION_REQUIRED") onPhoneVerification()
                _state.update { it.copy(isSending = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isSending = false, error = e.message ?: "Ошибка") }
            }
        }
    }
}
