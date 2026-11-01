package com.itd.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itd.app.R
import com.itd.app.data.model.Attachment
import com.itd.app.data.model.Post
import com.itd.app.ui.screens.notifications.nukstaBrush
import com.itd.app.util.TimeFormat

/** Provided by the main screen; null in previews and isolated screens. */
val LocalPostInteractor = staticCompositionLocalOf<PostInteractor?> { null }

private const val COLLAPSED_LINES = 12

@Composable
fun PostCard(
    post: Post,
    onOpen: () -> Unit,
    onAuthor: () -> Unit,
    onHashtag: ((String) -> Unit)? = null,
    onOpenOriginal: ((Post) -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val interactor = LocalPostInteractor.current
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    var reposting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    val portal by com.itd.app.halloween.HalloweenPortalManager.state.collectAsState()
    val haunt = if (portal.actionsAvailable) com.itd.app.halloween.rememberHalloweenHaunt(post.id).value else null
    val werewolf = haunt?.werewolf
    var flipped by rememberSaveable(post.id) { mutableStateOf(false) }
    val showNight = werewolf != null && (werewolf.night || (flipped && werewolf.revealed))
    if (post.isDeleted) {
        Text("Запись удалена", Modifier.fillMaxWidth().padding(16.dp), color = colors.onSurfaceVariant)
        if (showDivider) HorizontalDivider(color = colors.outlineVariant)
        return
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (post.isPinned) {
            Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                Text(" Закреплено", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ring = if (post.author.hasNuksta) Modifier.border(2.dp, nukstaBrush(), CircleShape) else Modifier
            UserAvatar(post.author.avatar, post.author.displayName, ring.clickable(onClick = onAuthor))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onAuthor)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.author.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (post.author.verified) {
                        Icon(painterResource(R.drawable.ic_verified), "Verified", Modifier.padding(start = 4.dp).size(16.dp), tint = colors.primary)
                    }
                    post.author.pin?.let { Text(" ${it.name}", fontSize = 13.sp) }
                    com.itd.app.halloween.PumpkinBadge(post.author.halloween, post.author.displayName)
                }
                val meta = listOfNotNull(
                    post.author.username?.let { "@$it" },
                    TimeFormat.relative(post.createdAt),
                    if (post.editedAt != null) "(ред.)" else null,
                ).joinToString(" · ")
                Text(meta, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
            }
            Box {
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_more), "More", Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
                DropdownMenu(menu, { menu = false }) {
                    if (post.isOwner) {
                        DropdownMenuItem({ Text(if (post.isPinned) "Открепить" else "Закрепить") }, {
                            menu = false
                            interactor?.togglePin(post)
                        })
                        DropdownMenuItem({ Text("Редактировать") }, {
                            menu = false
                            editing = true
                        })
                        DropdownMenuItem({ Text("Удалить пост", color = colors.error) }, {
                            menu = false
                            confirmDelete = true
                        })
                    } else {
                        DropdownMenuItem({ Text("Скопировать ссылку") }, {
                            menu = false
                            copyLink(context, PostInteractor.postUrl(post))
                        })
                        DropdownMenuItem({ Text("Пожаловаться") }, {
                            menu = false
                            reporting = true
                        })
                    }
                }
            }
        }
        if (werewolf != null) com.itd.app.halloween.WerewolfHeader(werewolf, post.isOwner, flipped) { flipped = it }
        if (showNight) com.itd.app.halloween.WerewolfNightContent(werewolf!!)
        if (!showNight) post.content?.takeIf { it.isNotBlank() }?.let { content ->
            var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
            var overflowing by remember(post.id, content) { mutableStateOf(false) }
            Spacer(Modifier.height(8.dp))
            PostText(
                content,
                post.spans,
                onHashtag = onHashtag,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                onOverflow = { overflowing = it },
            )
            if (overflowing && !expanded) {
                Text(
                    "Показать полностью",
                    Modifier.padding(top = 4.dp).clickable { expanded = true },
                    color = colors.primary,
                    fontSize = 14.sp,
                )
            }
        }
        if (!showNight) PostAttachments(post.attachments)
        haunt?.possession?.let { possession ->
            com.itd.app.halloween.PossessionPanel(post.id, post.author.username, possession, post.isOwner)
        }
        post.poll?.let { poll -> PostPollView(poll, onVote = { interactor?.vote(post, it) }) }
        post.originalPost?.let { original ->
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { onOpenOriginal?.invoke(original) ?: onOpen() },
            ) {
                if (original.isDeleted) {
                    Text("Запись удалена", Modifier.padding(12.dp), color = colors.onSurfaceVariant)
                } else {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(original.author.avatar, original.author.displayName, size = 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(original.author.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
                            Text(" · ${TimeFormat.relative(original.createdAt)}", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                        original.content?.takeIf { it.isNotBlank() }?.let {
                            PostText(it, original.spans, Modifier.padding(top = 6.dp), onHashtag = onHashtag, maxLines = 6)
                        }
                        PostAttachments(original.attachments)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                icon = {
                    val remote = if (post.isLiked) RemoteIcons.liked.value else RemoteIcons.like.value
                    if (remote != null) {
                        Icon(remote.image, "Нравится", Modifier.size(20.dp), tint = remote.tint ?: if (post.isLiked) RemoteIcons.LikedColor else LocalContentColor.current)
                    } else {
                        Icon(
                            painterResource(if (post.isLiked) R.drawable.ic_like_filled else R.drawable.ic_like),
                            "Нравится",
                            Modifier.size(20.dp),
                            tint = if (post.isLiked) RemoteIcons.LikedColor else LocalContentColor.current,
                        )
                    }
                },
                count = post.likesCount,
                onClick = { interactor?.toggleLike(post, nightWerewolf = werewolf?.night == true && !post.isOwner) },
            )
            ActionButton(
                icon = {
                    RemoteIcons.comment.value?.let { Icon(it.image, "Комментарии", Modifier.size(20.dp), tint = it.tint ?: LocalContentColor.current) }
                        ?: Icon(painterResource(R.drawable.ic_comment), "Комментарии", Modifier.size(20.dp))
                },
                count = post.commentsCount,
                onClick = onOpen,
            )
            ActionButton(
                icon = {
                    RemoteIcons.share.value?.let { Icon(it.image, "Поделиться", Modifier.size(20.dp), tint = it.tint ?: LocalContentColor.current) }
                        ?: Icon(painterResource(R.drawable.ic_repost), "Поделиться", Modifier.size(20.dp), tint = if (post.isReposted) colors.primary else LocalContentColor.current)
                },
                count = post.repostsCount,
                onClick = { share = true },
            )
            Spacer(Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_views), null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            Text(" ${post.viewsCount}", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
    }
    if (showDivider) HorizontalDivider(color = colors.outlineVariant)

    if (share) {
        AlertDialog(
            onDismissRequest = { share = false },
            title = { Text("Поделиться") },
            text = {
                Column {
                    if (!post.isOwner) {
                        Text("Сделать репост", Modifier.fillMaxWidth().clickable { share = false; reposting = true }.padding(vertical = 12.dp))
                    }
                    Text("Скопировать ссылку", Modifier.fillMaxWidth().clickable {
                        share = false
                        copyLink(context, PostInteractor.postUrl(post))
                    }.padding(vertical = 12.dp))
                    Text("Поделиться", Modifier.fillMaxWidth().clickable {
                        share = false
                        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, PostInteractor.postUrl(post))
                        context.startActivity(Intent.createChooser(intent, "Поделиться"))
                    }.padding(vertical = 12.dp))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { share = false }) { Text("Отмена") } },
        )
    }
    if (reposting) {
        var comment by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reposting = false },
            title = { Text("Репост") },
            text = {
                OutlinedTextField(comment, { comment = it }, placeholder = { Text("Добавьте комментарий...") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    reposting = false
                    interactor?.repost(post, comment)
                }) { Text("Опубликовать") }
            },
            dismissButton = { TextButton(onClick = { reposting = false }) { Text("Отмена") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить публикацию?") },
            text = { Text("Если вы удалите эту публикацию, восстановить её будет невозможно.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    interactor?.delete(post)
                }) { Text("Удалить", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отменить") } },
        )
    }
    if (reporting) {
        ReportDialog(onDismiss = { reporting = false }) { reason ->
            reporting = false
            interactor?.report("post", post.id, reason)
        }
    }
    if (editing) EditPostDialog(post, interactor) { editing = false }
}

@Composable
private fun EditPostDialog(post: Post, interactor: PostInteractor?, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(post.content.orEmpty()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val changed = text != post.content.orEmpty()
    AlertDialog(
        onDismissRequest = { if (changed) confirmDiscard = true else onClose() },
        title = { Text("Редактировать") },
        text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)) },
        confirmButton = {
            TextButton(onClick = { interactor?.edit(post, text, onClose) }, enabled = changed && text.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = { if (changed) confirmDiscard = true else onClose() }) { Text("Отмена") } },
    )
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Несохранённые изменения") },
            text = { Text("У вас есть несохранённые изменения.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    interactor?.edit(post, text, onClose)
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text("Не сохранять") }
            },
        )
    }
}

/** "Phone confirmation required" dialog shown before the first post or comment. */
@Composable
fun PhoneVerificationDialog(userId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтверждение телефона") },
        text = { Text("Для публикации постов и комментариев необходимо подтвердить номер телефона через Telegram-бота.") },
        confirmButton = {
            TextButton(onClick = {
                PostInteractor.openVerificationBot(context, userId)
                onDismiss()
            }) { Text("Открыть Telegram") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отменить") } },
    )
}

fun copyLink(context: android.content.Context, url: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ITD", url))
    Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
}

@Composable
private fun ActionButton(icon: @Composable () -> Unit, count: Int, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        if (count > 0) Text(" $count", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PostAttachments(attachments: List<Attachment>) {
    val sorted = attachments.sortedBy { it.order }
    val images = sorted.filter { it.type == "image" || it.mimeType?.startsWith("image/") == true }
    if (images.isNotEmpty()) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            images.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { image ->
                        val ratio = if ((image.width ?: 0) > 0 && (image.height ?: 0) > 0) image.width!!.toFloat() / image.height!! else 1f
                        AsyncImage(
                            image.url,
                            image.filename,
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .let { if (row.size == 1) it.aspectRatio(ratio.coerceIn(0.6f, 2f)) else it.aspectRatio(1f) },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
    sorted.forEach { attachment ->
        when {
            attachment.type == "video" || attachment.mimeType?.startsWith("video/") == true -> MediaPlayer(
                attachment.url,
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).aspectRatio(
                    if ((attachment.width ?: 0) > 0 && (attachment.height ?: 0) > 0) attachment.width!!.toFloat() / attachment.height!! else 16f / 9f,
                ),
            )
            attachment.type == "audio" || attachment.mimeType?.startsWith("audio/") == true -> {
                attachment.filename?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium) }
                MediaPlayer(attachment.url, Modifier.fillMaxWidth().height(96.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)))
            }
        }
    }
}
