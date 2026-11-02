package com.itd.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itd.app.R
import com.itd.app.data.model.Attachment
import com.itd.app.data.model.Post
import com.itd.app.data.model.PostSpan
import com.itd.app.data.model.User
import com.itd.app.util.TimeFormat

/** Provided by the main screen; null in previews and isolated screens. */
val LocalPostInteractor = staticCompositionLocalOf<PostInteractor?> { null }

private val RepostedColor = Color(0xFF00BA7C)
private val OnlineColor = Color(0xFF4CAF50)

/** Post in a list; [detail] is the expanded layout used at the top of the post screen. */
@Composable
fun PostCard(
    post: Post,
    onOpen: () -> Unit,
    onAuthor: () -> Unit,
    onHashtag: ((String) -> Unit)? = null,
    onOpenOriginal: ((Post) -> Unit)? = null,
    showDivider: Boolean = true,
    detail: Boolean = false,
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
        Text("Запись удалена", Modifier.fillMaxWidth().padding(16.dp), color = colors.onSurfaceVariant, fontSize = 14.sp)
        if (showDivider) HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        return
    }
    val inset = if (detail) PaddingValues(horizontal = 16.dp) else PaddingValues(start = 66.dp, end = 16.dp)
    Column(Modifier.fillMaxWidth().let { if (detail) it else it.clickable(onClick = onOpen) }) {
        if (post.isPinned && !detail) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_pin), "Pinned", Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("Закреплено", color = colors.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = if (post.isPinned && !detail) 4.dp else 14.dp, end = 16.dp),
            verticalAlignment = if (detail) Alignment.CenterVertically else Alignment.Top,
        ) {
            AuthorAvatar(post.author, 40.dp, 20.sp, Modifier.noRippleClickable(onAuthor))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                AuthorLine(post.author, post.createdAt, post.editedAt != null, 15.sp, 14.sp, 12.sp, if (detail) 20.dp else 18.dp, 14.dp, onAuthor)
                if (!detail) {
                    Spacer(Modifier.height(4.dp))
                    if (werewolf != null) com.itd.app.halloween.WerewolfHeader(werewolf, post.isOwner, flipped) { flipped = it }
                    if (showNight) com.itd.app.halloween.WerewolfNightContent(werewolf!!)
                    else post.content?.takeIf { it.isNotEmpty() }?.let { PostBody(post.id, it, post.spans, 800, 15, onHashtag) }
                }
            }
            Box {
                Icon(
                    painterResource(R.drawable.ic_more),
                    "More",
                    Modifier.size(32.dp).noRippleClickable { menu = true }.padding(4.dp),
                    tint = colors.onSurfaceVariant,
                )
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
        if (detail) {
            if (werewolf != null) {
                Box(Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp)) {
                    com.itd.app.halloween.WerewolfHeader(werewolf, post.isOwner, flipped) { flipped = it }
                }
            }
            if (showNight) {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp)) { com.itd.app.halloween.WerewolfNightContent(werewolf!!) }
            } else {
                post.content?.takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.height(10.dp))
                    PostText(it, post.spans, Modifier.padding(horizontal = 16.dp), onHashtag = onHashtag, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
        post.originalPost?.let { original ->
            Spacer(Modifier.height(10.dp))
            QuotedPost(original, detail, onHashtag) { onOpenOriginal?.invoke(original) ?: onOpen() }
        }
        if (!showNight) {
            post.attachments.firstOrNull { it.isVideo }?.let { video ->
                Spacer(Modifier.height(10.dp))
                MediaPlayer(video.url, Modifier.padding(inset).fillMaxWidth().clip(RoundedCornerShape(16.dp)).aspectRatio(video.ratio))
            }
            val images = post.attachments.filter { it.isImage }
            if (images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ImageGallery(images, inset)
            }
        }
        post.poll?.let { poll ->
            Spacer(Modifier.height(10.dp))
            PostPollView(poll, onVote = { interactor?.vote(post, it) }, modifier = Modifier.padding(start = if (detail) 16.dp else 66.dp, end = 16.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = if (detail) 8.dp else 58.dp, top = 2.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostActions(post, onComments = onOpen, onShare = { share = true }, likeIconSize = 20.dp) {
                interactor?.toggleLike(post, nightWerewolf = werewolf?.night == true && !post.isOwner)
            }
            PostReach(post)
        }
        haunt?.possession?.let { possession ->
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                com.itd.app.halloween.PossessionPanel(post.id, post.author.username, possession, post.isOwner)
            }
        }
        if (showDivider) HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
    }

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

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = clickable(MutableInteractionSource(), null, onClick = onClick)

/** Emoji avatar on a round plate, with the online dot. */
@Composable
fun AuthorAvatar(author: User, size: Dp, emojiSize: TextUnit, modifier: Modifier = Modifier) {
    Box(modifier) {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            val avatar = author.avatar
            if (avatar != null && (avatar.startsWith("https://") || avatar.startsWith("http://"))) {
                AsyncImage(avatar, author.displayName, Modifier.size(size), contentScale = ContentScale.Crop)
            } else {
                Text(avatar ?: "?", fontSize = emojiSize)
            }
        }
        if (author.online) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(12.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape).padding(2.dp)
                    .background(OnlineColor, CircleShape),
            )
        }
    }
}

/** Name, verification mark, pin, event badge, relative time and the edited mark in one row. */
@Composable
private fun AuthorLine(
    author: User,
    createdAt: String,
    edited: Boolean,
    nameSize: TextUnit,
    timeSize: TextUnit,
    editedSize: TextUnit,
    pinSize: Dp,
    verifiedSize: Dp,
    onAuthor: () -> Unit,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        UserName(author.displayName, author.hasNuksta, nameSize, Modifier.weight(1f, fill = false).noRippleClickable(onAuthor))
        if (author.verified) {
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_verified), "Verified", Modifier.size(verifiedSize), tint = Color.Unspecified)
        }
        author.pin?.let {
            Spacer(Modifier.width(4.dp))
            PinIcon(it, pinSize)
        }
        com.itd.app.halloween.PumpkinBadge(author.halloween, author.displayName)
        Spacer(Modifier.width(6.dp))
        Text(TimeFormat.relative(createdAt), color = secondary, fontSize = timeSize, maxLines = 1)
        if (edited) {
            Spacer(Modifier.width(4.dp))
            Text("(ред.)", color = secondary.copy(alpha = 0.6f), fontSize = editedSize, maxLines = 1)
        }
    }
}

/** Post text cut to [maxChars] characters or [maxLines] lines until expanded. */
@Composable
private fun PostBody(id: String, content: String, spans: List<PostSpan>, maxChars: Int, maxLines: Int, onHashtag: ((String) -> Unit)?) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val shown = if (expanded) content else truncatePost(content, maxChars, maxLines)
    val shownSpans = if (shown.length < content.length) clipSpans(spans, shown.length) else spans
    PostText(shown, shownSpans, onHashtag = onHashtag, fontSize = 15.sp, lineHeight = 22.sp)
    if (shown.length < content.length && !expanded) {
        Text(
            "Показать полностью",
            Modifier.padding(top = 2.dp).noRippleClickable { expanded = true },
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
    }
}

fun truncatePost(text: String, maxChars: Int, maxLines: Int): String {
    val lines = text.split("\n")
    var cut = if (lines.size > maxLines) lines.take(maxLines).joinToString("\n") else text
    if (cut.length > maxChars) cut = cut.take(maxChars)
    return if (cut.length >= text.length) cut else cut.trimEnd() + "..."
}

fun clipSpans(spans: List<PostSpan>, length: Int): List<PostSpan> = spans.mapNotNull { span ->
    val visible = minOf(span.length, length - span.offset)
    if (span.offset < length && visible > 0) span.copy(length = visible) else null
}

@Composable
private fun QuotedPost(post: Post, detail: Boolean, onHashtag: ((String) -> Unit)?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.padding(if (detail) PaddingValues(horizontal = 16.dp) else PaddingValues(start = 66.dp, end = 16.dp))
            .fillMaxWidth().border(1.dp, colors.outlineVariant, shape).clip(shape).clickable(onClick = onClick),
    ) {
        if (post.isDeleted) {
            Text("Запись удалена", Modifier.padding(16.dp), color = colors.onSurfaceVariant, fontSize = 14.sp)
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AuthorAvatar(post.author.copy(online = false), 28.dp, 14.sp)
            Spacer(Modifier.width(8.dp))
            AuthorLine(post.author, post.createdAt, post.editedAt != null, 13.sp, 12.sp, 10.sp, 18.dp, 12.dp, onClick)
        }
        post.content?.takeIf { it.isNotEmpty() }?.let { content ->
            val shown = truncatePost(content, 400, 10)
            Spacer(Modifier.height(6.dp))
            PostText(
                shown,
                if (shown.length < content.length) clipSpans(post.spans, shown.length) else post.spans,
                Modifier.padding(horizontal = 12.dp),
                onHashtag = onHashtag,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        post.attachments.firstOrNull { it.isVideo }?.let { video ->
            Spacer(Modifier.height(8.dp))
            MediaPlayer(video.url, Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).aspectRatio(video.ratio))
        }
        val images = post.attachments.filter { it.isImage }
        if (images.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ImageGallery(images, PaddingValues(horizontal = 12.dp))
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, end = 12.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostActions(post, onComments = onClick, onShare = onClick, likeIconSize = 20.dp, onLike = onClick)
            PostReach(post)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PostActions(post: Post, onComments: () -> Unit, onShare: () -> Unit, likeIconSize: Dp, onLike: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        val likeColor = if (post.isLiked) RemoteIcons.LikedColor else secondary
        ActionButton(post.likesCount, likeColor, onLike) {
            val remote = if (post.isLiked) RemoteIcons.liked.value else RemoteIcons.like.value
            if (remote != null) Icon(remote.image, "Нравится", Modifier.size(likeIconSize), tint = remote.tint ?: likeColor)
            else Icon(painterResource(if (post.isLiked) R.drawable.ic_like_filled else R.drawable.ic_like), "Нравится", Modifier.size(likeIconSize), tint = likeColor)
        }
        Spacer(Modifier.width(5.dp))
        val commentRemote = RemoteIcons.comment.value
        val commentColor = commentRemote?.tint ?: secondary
        ActionButton(post.commentsCount, commentColor, onComments) {
            if (commentRemote != null) Icon(commentRemote.image, "Комментарии", Modifier.size(20.dp), tint = commentColor)
            else Icon(painterResource(R.drawable.ic_comment), "Комментарии", Modifier.size(20.dp), tint = commentColor)
        }
        Spacer(Modifier.width(5.dp))
        val shareRemote = RemoteIcons.share.value
        val shareColor = shareRemote?.tint ?: if (post.isReposted) RepostedColor else secondary
        ActionButton(post.repostsCount, shareColor, onShare) {
            if (shareRemote != null) Icon(shareRemote.image, "Поделиться", Modifier.size(20.dp), tint = shareColor)
            else Icon(painterResource(R.drawable.ic_repost), "Поделиться", Modifier.size(20.dp), tint = shareColor)
        }
    }
}

/** Dominant reaction emoji and the view counter. */
@Composable
private fun PostReach(post: Post) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        post.dominantEmoji?.let {
            Text(it, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
        }
        Icon(painterResource(R.drawable.ic_views), null, Modifier.size(20.dp), tint = secondary)
        Spacer(Modifier.width(6.dp))
        Text(post.viewsCount.toString(), color = secondary, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
    }
}

@Composable
private fun ActionButton(count: Int, color: Color, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        Modifier.animateContentSize().noRippleClickable(onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), color = color, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
    }
}

private val Attachment.isVideo get() = type == "video"
private val Attachment.isImage get() = type == "image"
private val Attachment.ratio: Float
    get() {
        val w = width ?: 0
        val h = height ?: 0
        return if (w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.05f, 20f) else 4f / 3f
    }

/** One image fills the width; several scroll sideways with the next one peeking in. */
@Composable
fun ImageGallery(images: List<Attachment>, padding: PaddingValues) {
    val shape = RoundedCornerShape(16.dp)
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    if (images.size == 1) {
        val image = images.first()
        AsyncImage(
            image.url,
            image.filename,
            Modifier.padding(padding).fillMaxWidth().aspectRatio(image.ratio.coerceIn(0.8f, 1.91f)).clip(shape).border(0.5.dp, border, shape),
            contentScale = ContentScale.Crop,
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val direction = LocalLayoutDirection.current
        val itemMax = maxWidth - padding.calculateStartPadding(direction) - padding.calculateEndPadding(direction) - 40.dp
        val widest = images.maxOf { it.ratio.coerceIn(0.8f, 1.91f) }
        val height = (itemMax / widest).coerceIn(180.dp, 360.dp)
        LazyRow(Modifier.fillMaxWidth(), contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(images, key = { it.id }) { image ->
                AsyncImage(
                    image.url,
                    image.filename,
                    Modifier.height(height).aspectRatio(image.ratio.coerceIn(0.8f, 1.91f), matchHeightConstraintsFirst = true)
                        .widthIn(max = itemMax).clip(shape).border(0.5.dp, border, shape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** Audio and other non-visual attachments are not rendered in the list. */
@Composable
fun PostAttachments(attachments: List<Attachment>) {
    attachments.firstOrNull { it.isVideo }?.let { video ->
        MediaPlayer(video.url, Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).aspectRatio(video.ratio))
    }
    val images = attachments.filter { it.isImage }
    if (images.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        ImageGallery(images, PaddingValues())
    }
}
