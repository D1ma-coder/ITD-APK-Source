package com.itd.app.ui.screens.postdetail

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.itd.app.R
import com.itd.app.data.model.Comment
import com.itd.app.ui.common.EmptyState
import com.itd.app.ui.common.ErrorContent
import com.itd.app.ui.common.LoadState
import com.itd.app.ui.common.LoadingContent
import com.itd.app.ui.components.LocalPostInteractor
import com.itd.app.ui.components.MediaPlayer
import com.itd.app.ui.components.PostCard
import com.itd.app.ui.components.PostText
import com.itd.app.ui.components.ReportDialog
import com.itd.app.ui.components.UserAvatar
import com.itd.app.util.TimeFormat
import java.io.File

@Composable
fun PostDetailScreen(
    viewModel: PostDetailViewModel,
    onProfile: (String) -> Unit,
    commentId: String? = null,
    ownUserId: String? = null,
    onBack: (() -> Unit)? = null,
    onHashtag: ((String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }
    val listState = rememberLazyListState()
    var focused by rememberSaveable(commentId) { mutableStateOf(false) }
    val threads = (state as? LoadState.Ready)?.data?.threads.orEmpty()
    LaunchedEffect(commentId, threads.size) {
        if (commentId != null && !focused) {
            val index = threads.indexOfFirst { thread -> thread.comment.id == commentId || thread.replies.any { it.id == commentId } }
            if (index >= 0) {
                listState.scrollToItem(index + 2)
                focused = true
            }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        if (onBack != null) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_chevron_back), "Назад", Modifier.size(22.dp))
                }
            }
        }
        when (val value = state) {
            LoadState.Loading -> LoadingContent()
            is LoadState.Error -> ErrorContent(value.message, { viewModel.refresh() })
            is LoadState.Ready -> {
                val content = value.data
                LazyColumn(Modifier.weight(1f), state = listState) {
                    item { PostCard(content.post, {}, { onProfile(content.post.author.username ?: content.post.author.id) }, onHashtag) }
                    item { SortSelector(content.sort, viewModel::setSort) }
                    when {
                        content.commentsOffline -> item {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Нет подключения к интернету", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = viewModel::retryComments) { Text("Повторить") }
                            }
                        }
                        content.commentsLoaded && content.threads.isEmpty() -> item { EmptyState("Пока нет комментариев") }
                        !content.commentsLoaded -> item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        }
                    }
                    items(content.threads, key = { it.comment.id }) { thread ->
                        CommentRow(thread.comment, thread.comment.id == commentId, ownUserId, viewModel, onProfile, indent = false)
                        thread.replies.forEach { reply ->
                            CommentRow(reply, reply.id == commentId, ownUserId, viewModel, onProfile, indent = true)
                        }
                        if (thread.hiddenReplies > 0) {
                            Text(
                                "Показать ещё ${thread.hiddenReplies} ${TimeFormat.plural(thread.hiddenReplies, "ответ", "ответа", "ответов")}",
                                Modifier.padding(start = 66.dp, bottom = 8.dp).clickable { viewModel.loadReplies(thread.comment.id) },
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (content.hasMore) {
                        item {
                            LaunchedEffect(content.cursor) { viewModel.loadMoreComments() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        }
                    }
                }
                CommentComposer(content, viewModel)
            }
        }
    }
}

@Composable
private fun SortSelector(sort: CommentSort, onSelect: (CommentSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.clickable { open = true }, verticalAlignment = Alignment.CenterVertically) {
            Text(sort.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(16.dp))
        }
        DropdownMenu(open, { open = false }) {
            CommentSort.entries.forEach { option ->
                DropdownMenuItem({ Text(option.title) }, {
                    open = false
                    onSelect(option)
                })
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    highlighted: Boolean,
    ownUserId: String?,
    viewModel: PostDetailViewModel,
    onProfile: (String) -> Unit,
    indent: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val interactor = LocalPostInteractor.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(if (highlighted) colors.primary.copy(alpha = 0.08f) else colors.background)
            .padding(start = if (indent) 56.dp else 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
    ) {
        UserAvatar(comment.author.avatar, comment.author.displayName, Modifier.clickable { onProfile(comment.author.username ?: comment.author.id) }, size = if (indent) 32.dp else 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.author.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                Text(" · ${TimeFormat.relative(comment.createdAt)}", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            comment.replyTo?.let { target ->
                Text("В ответ ${target.username?.let { "@$it" } ?: target.displayName}", fontSize = 12.sp, color = colors.primary)
            }
            if (comment.content.isNotBlank()) PostText(comment.content, emptyList(), Modifier.padding(top = 2.dp))
            comment.attachments.forEach { attachment ->
                when {
                    attachment.type == "audio" || attachment.mimeType?.startsWith("audio/") == true ->
                        MediaPlayer(attachment.url, Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)))
                    else -> AsyncImage(
                        attachment.url,
                        attachment.filename,
                        Modifier.padding(top = 6.dp).size(160.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ответить", Modifier.clickable { viewModel.replyTo(comment) }.padding(end = 16.dp, top = 4.dp, bottom = 4.dp), fontSize = 13.sp, color = colors.onSurfaceVariant)
                Row(Modifier.clickable { viewModel.toggleCommentLike(comment) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(if (comment.isLiked) R.drawable.ic_like_filled else R.drawable.ic_like),
                        "Нравится",
                        Modifier.size(16.dp),
                        tint = if (comment.isLiked) com.itd.app.ui.components.RemoteIcons.LikedColor else colors.onSurfaceVariant,
                    )
                    if (comment.likesCount > 0) Text(" ${comment.likesCount}", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
        }
        Box {
            Box(Modifier.size(28.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_more), "More", Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            }
            DropdownMenu(menu, { menu = false }) {
                if (comment.author.id == ownUserId) {
                    DropdownMenuItem({ Text("Удалить комментарий", color = colors.error) }, { menu = false; confirmDelete = true })
                } else {
                    DropdownMenuItem({ Text("Пожаловаться") }, { menu = false; reporting = true })
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить комментарий?") },
            text = { Text("Если вы удалите этот комментарий, восстановить его будет невозможно.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteComment(comment) }) { Text("Удалить", color = colors.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отменить") } },
        )
    }
    if (reporting) {
        ReportDialog(onDismiss = { reporting = false }) { reason ->
            reporting = false
            interactor?.report("comment", comment.id, reason)
        }
    }
}

@Composable
private fun CommentComposer(content: PostDetailContent, viewModel: PostDetailViewModel) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var draft by rememberSaveable { mutableStateOf("") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }
    var recordStart by remember { mutableStateOf(0L) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.isNotEmpty()) viewModel.addAttachments(uris, audio = false)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val file = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        recorder = runCatching {
            MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.getOrNull()
        recordFile = file
        recordStart = System.currentTimeMillis()
    }

    fun stopRecording() {
        val active = recorder ?: return
        recorder = null
        val duration = System.currentTimeMillis() - recordStart
        runCatching { active.stop() }
        active.release()
        val file = recordFile ?: return
        if (duration < 1_000) {
            file.delete()
            Toast.makeText(context, "Удерживайте дольше для записи", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addAttachments(listOf(Uri.fromFile(file)), audio = true)
        }
    }

    Column(Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding()) {
        HorizontalDivider(color = colors.outlineVariant)
        content.replyTo?.let { target ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("В ответ ${target.author.displayName}", Modifier.weight(1f), fontSize = 13.sp, color = colors.primary)
                Icon(painterResource(R.drawable.ic_close), "Закрыть", Modifier.size(18.dp).clickable { viewModel.replyTo(null) })
            }
        }
        if (content.attachments.isNotEmpty()) {
            val photos = content.attachments.count { !it.isAudio }
            Text(
                if (photos == content.attachments.size) "Фото ($photos)" else "Вложения (${content.attachments.size})",
                Modifier.padding(start = 16.dp, top = 6.dp),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content.attachments.forEach { attachment ->
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant)) {
                        if (attachment.isAudio) {
                            Text("Голосовое сообщение", Modifier.align(Alignment.Center).padding(4.dp), fontSize = 10.sp)
                        } else {
                            AsyncImage(attachment.uri, "Фото", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        if (attachment.uploadedId == null && !attachment.failed) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center).size(20.dp), strokeWidth = 2.dp)
                        }
                        Icon(
                            painterResource(R.drawable.ic_close),
                            "Удалить",
                            Modifier.align(Alignment.TopEnd).padding(2.dp).size(16.dp).clickable { viewModel.removeAttachment(attachment.uri) },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_gallery),
                "Фото",
                Modifier.size(40.dp).clip(CircleShape).clickable {
                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }.padding(9.dp),
                tint = colors.onSurfaceVariant,
            )
            OutlinedTextField(
                draft,
                { draft = it },
                Modifier.weight(1f),
                placeholder = { Text("Комментарий…") },
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
            )
            if (draft.isBlank() && content.attachments.isEmpty()) {
                Icon(
                    painterResource(if (recorder != null) R.drawable.ic_stop else R.drawable.ic_mic),
                    "Голосовое сообщение",
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (recorder != null) colors.error.copy(alpha = 0.15f) else colors.surface)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    startRecording()
                                    tryAwaitRelease()
                                    stopRecording()
                                },
                            )
                        }.padding(9.dp),
                    tint = if (recorder != null) colors.error else colors.onSurfaceVariant,
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_send),
                    "Отправить",
                    Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !content.sending) {
                        viewModel.send(draft) { draft = "" }
                    }.padding(9.dp),
                    tint = colors.primary,
                )
            }
        }
    }
}
