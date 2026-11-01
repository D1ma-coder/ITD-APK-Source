package com.itd.app.ui.screens.createpost

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.itd.app.R
import com.itd.app.data.model.PostSpan
import com.itd.app.ui.screens.camera.CameraActivity

private val formats = listOf(
    "plain" to "Обычный",
    "bold" to "Жирный",
    "italic" to "Курсив",
    "underline" to "Подчёркнутый",
    "strikethrough" to "Зачёркнутый",
    "monospace" to "Моноширинный",
    "spoiler" to "Спойлер",
    "link" to "Ссылка",
)

private class SpanTransformation(private val spans: List<PostSpan>, private val linkColor: Color, private val spoiler: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        spans.forEach { span ->
            val start = span.offset.coerceIn(0, text.length)
            val end = (span.offset + span.length).coerceIn(0, text.length)
            if (start >= end) return@forEach
            val style = when (span.type) {
                "bold" -> SpanStyle(fontWeight = FontWeight.Bold)
                "italic" -> SpanStyle(fontStyle = FontStyle.Italic)
                "underline" -> SpanStyle(textDecoration = TextDecoration.Underline)
                "strikethrough" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                "monospace" -> SpanStyle(fontFamily = FontFamily.Monospace)
                "spoiler" -> SpanStyle(background = spoiler)
                "link" -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                else -> SpanStyle()
            }
            builder.addStyle(style, start, end)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun CreatePostScreen(viewModel: CreatePostViewModel, onBack: () -> Unit = {}, onCreated: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var linkDialog by remember { mutableStateOf(false) }
    var nightHelp by remember { mutableStateOf(false) }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { viewModel.attach(it) }
    val pickNight = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { viewModel.attach(it, night = true) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uri = if (Build.VERSION.SDK_INT >= 33) {
                data?.getParcelableExtra(CameraActivity.RESULT_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(CameraActivity.RESULT_URI) as? Uri
            }
            if (uri != null) viewModel.attach(listOf(uri))
        }
    }
    LaunchedEffect(state.createdPostId) {
        state.createdPostId?.let { id ->
            state.resultMessage?.let { message ->
                val detail = if (message == "Пост стал оборотнем") "\nВ полночь перевернётся. Ночная сторона видна до 9 утра" else ""
                Toast.makeText(context, message + detail, Toast.LENGTH_LONG).show()
                viewModel.consumeResult()
            }
            onCreated(id)
        }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_chevron_back), "Назад", Modifier.size(22.dp))
            }
            Text("Новый пост", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (state.canPublish) colors.primary else colors.surfaceVariant)
                    .clickable(enabled = state.canPublish, onClick = viewModel::submit).padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                } else {
                    Text("Опубликовать", color = if (state.canPublish) colors.onPrimary else colors.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                if (state.content.isEmpty()) Text("О чём думаете?", color = colors.onSurfaceVariant, fontSize = 17.sp)
                BasicTextField(
                    state.text,
                    viewModel::onTextChange,
                    Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    textStyle = TextStyle(color = colors.onBackground, fontSize = 17.sp),
                    cursorBrush = SolidColor(colors.primary),
                    visualTransformation = SpanTransformation(state.spans, colors.primary, colors.onSurfaceVariant.copy(alpha = 0.35f)),
                )
            }
            if (!state.text.selection.collapsed) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    formats.forEach { (type, label) ->
                        Text(
                            label,
                            Modifier.clip(RoundedCornerShape(50)).background(colors.surfaceVariant)
                                .clickable { if (type == "link") linkDialog = true else viewModel.format(type) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            AttachmentStrip(state.attachments) { viewModel.removeAttachment(it) }
            state.poll?.let { poll -> PollEditor(poll, viewModel::setPoll) }
            if (state.nightEnabled) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF14142B)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌙 Ночная версия", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("что это?", Modifier.clickable { nightHelp = true }, color = Color(0xFFB3AFFA), fontSize = 13.sp)
                    }
                    BasicTextField(
                        state.nightText,
                        viewModel::onNightTextChange,
                        Modifier.fillMaxWidth().heightIn(min = 60.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        decorationBox = { inner ->
                            if (state.nightText.isEmpty()) Text("Что покажется в полночь?", color = Color.White.copy(alpha = 0.5f))
                            inner()
                        },
                    )
                    AttachmentStrip(state.nightImages) { viewModel.removeAttachment(it, night = true) }
                    Text(
                        "Фото",
                        Modifier.clickable { pickNight.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        color = Color(0xFFB3AFFA),
                    )
                }
            }
            state.error?.let { Text(it, color = colors.error) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(R.drawable.ic_gallery, "Фото") { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
            ToolIcon(R.drawable.ic_camera, "Камера") { camera.launch(Intent(context, CameraActivity::class.java)) }
            ToolIcon(R.drawable.ic_survey, "Опрос") { viewModel.setPoll(if (state.poll == null) PollDraft() else null) }
            ToolIcon(R.drawable.ic_link, "Ссылка") { if (!state.text.selection.collapsed) linkDialog = true }
            if (com.itd.app.halloween.HalloweenPortalManager.actionsAvailable) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (state.nightEnabled) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { viewModel.setNightEnabled(!state.nightEnabled) },
                    contentAlignment = Alignment.Center,
                ) { Text("🌙", fontSize = 20.sp) }
            }
        }
    }
    if (linkDialog) LinkDialog(onDismiss = { linkDialog = false }) { url ->
        linkDialog = false
        viewModel.format("link", url)
    }
    if (nightHelp) {
        AlertDialog(
            onDismissRequest = { nightHelp = false },
            title = { Text("Как это работает") },
            text = { Text("Напиши обычный пост, затем нажми луну и добавь ночную версию. В ней могут быть свой текст и картинки.") },
            confirmButton = { TextButton(onClick = { nightHelp = false }) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun ToolIcon(icon: Int, description: String, tint: Color? = null, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), description, Modifier.size(22.dp), tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AttachmentStrip(items: List<DraftAttachment>, onRemove: (Uri) -> Unit) {
    if (items.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (item.uploading) CircularProgressIndicator(progress = { item.progress }, modifier = Modifier.align(Alignment.Center).size(28.dp))
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable { onRemove(item.uri) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_close), "Удалить", Modifier.size(12.dp), tint = Color.White) }
            }
        }
    }
}

@Composable
private fun PollEditor(poll: PollDraft, onChange: (PollDraft?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(poll.question, { onChange(poll.copy(question = it)) }, Modifier.weight(1f), placeholder = { Text("Опрос") }, singleLine = true)
            Icon(painterResource(R.drawable.ic_close), "Удалить", Modifier.padding(start = 8.dp).size(20.dp).clickable { onChange(null) })
        }
        poll.options.forEachIndexed { index, option ->
            OutlinedTextField(
                option,
                { value -> onChange(poll.copy(options = poll.options.toMutableList().also { it[index] = value })) },
                Modifier.fillMaxWidth(),
                placeholder = { Text("Вариант ${index + 1}") },
                singleLine = true,
            )
        }
        if (poll.options.size < 10) {
            Text(
                "Добавить вариант...",
                Modifier.clickable { onChange(poll.copy(options = poll.options + "")) }.padding(vertical = 6.dp),
                color = colors.primary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(poll.multipleChoice, { onChange(poll.copy(multipleChoice = it)) })
            Text("Несколько вариантов ответа")
        }
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    val clipboard = remember {
        context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.text?.toString()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ссылка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, placeholder = { Text("Введите URL") }, singleLine = true)
                clipboard?.let { copied ->
                    Text("Вставить «${copied.take(40)}»", Modifier.clickable { url = copied }, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url.trim()) }, enabled = url.startsWith("http://") || url.startsWith("https://")) { Text("ОК") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
