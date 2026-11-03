package com.itd.app.ui.screens.createpost

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itd.app.R
import com.itd.app.data.model.User
import com.itd.app.halloween.HalloweenPortalManager
import com.itd.app.ui.components.ItdMenu
import com.itd.app.ui.components.ItdMenuItem
import com.itd.app.ui.components.RichTextEditor
import com.itd.app.ui.components.RichTextState
import com.itd.app.ui.components.UserName
import com.itd.app.ui.components.glassRim
import com.itd.app.ui.screens.bannerpainter.DrawingEditor
import com.itd.app.ui.screens.bannerpainter.renderDrawing
import com.itd.app.ui.screens.camera.CameraActivity
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Composable
fun CreatePostScreen(
    viewModel: CreatePostViewModel,
    user: User?,
    onBack: () -> Unit = {},
    title: String = "Новый пост",
    onCreated: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val editor = remember { RichTextState() }
    val pollOptions = remember { mutableStateListOf("", "", "") }
    var attachMenu by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var nightHelp by remember { mutableStateOf(false) }
    val night = state.nightEnabled
    val subscriber = user?.subscription?.isActive == true
    // a video is for subscribers, alone, outside the night version
    val videoAllowed = subscriber && state.photos.isEmpty() && state.video == null && !night
    val isVideo = { uri: Uri -> context.contentResolver.getType(uri)?.startsWith("video/") == true }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia((10 - state.photos.size).coerceAtLeast(2))) {
        viewModel.attach(it, isVideo, night)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data
        val uri = if (Build.VERSION.SDK_INT >= 33) {
            data?.getParcelableExtra(CameraActivity.RESULT_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            data?.getParcelableExtra(CameraActivity.RESULT_URI) as? Uri
        }
        val video = data?.getStringExtra(CameraActivity.RESULT_MODE) == "video"
        if (uri != null) viewModel.attach(listOf(uri), { video }, night)
    }
    val pollFilled = pollOptions.count { it.isNotBlank() } >= 2
    val hasContent = editor.text.isNotBlank() || state.photos.isNotEmpty() || state.video != null || (state.pollEnabled && pollFilled)
    val validation = viewModel.validation(editor.text)
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
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null) { editor.hideKeyboard() },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = 10.dp).clip(sheetShape).background(colors.surface)
                .imePadding().navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(colors.onSurfaceVariant.copy(alpha = 0.4f)))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = 0.1f)).glassRim()
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            editor.hideKeyboard()
                            onBack()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_chevron_back), "Назад", Modifier.size(21.dp).offset(x = (-1).dp), tint = colors.onSurface)
                }
                Text(title, Modifier.weight(1f), colors.onSurface, 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.width(40.dp))
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text(user?.avatar ?: "?", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserName(user?.displayName.orEmpty(), user?.hasNuksta == true, 15.sp)
                            if (user?.verified == true) {
                                Spacer(Modifier.width(4.dp))
                                Icon(painterResource(R.drawable.ic_verified), "Verified", Modifier.size(14.dp), tint = Color.Unspecified)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        RichTextEditor(
                            editor, Modifier.fillMaxWidth(),
                            TextStyle(color = colors.onSurface, fontSize = 15.sp, lineHeight = 22.sp),
                            placeholder = {
                                Text(
                                    if (night) "Что покажется в полночь?" else "О чём думаете?",
                                    color = colors.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 15.sp, lineHeight = 22.sp,
                                )
                            },
                        )
                    }
                }
                val enter = expandVertically(spring(stiffness = 400f)) + fadeIn(spring(stiffness = 400f))
                val exit = shrinkVertically(spring(stiffness = 400f)) + fadeOut(spring(stiffness = 400f))
                AnimatedVisibility(state.photos.isNotEmpty() && !night, enter = enter, exit = exit) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(contentPadding = PaddingValues(start = 66.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.photos, key = { it.localId }) { photo ->
                                PendingPhotoView(photo, { viewModel.remove(photo) }, { viewModel.retry(photo) })
                            }
                        }
                    }
                }
                AnimatedVisibility(state.video != null && !night, enter = enter, exit = exit) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.padding(start = 66.dp, end = 16.dp)) {
                            state.video?.let { video -> PendingVideoView(video, { viewModel.remove(video) }, { viewModel.retry(video) }) }
                        }
                    }
                }
                AnimatedVisibility(
                    state.pollEnabled && !night,
                    enter = expandVertically(spring(0.8f, 400f)) + fadeIn(tween(200)),
                    exit = shrinkVertically(spring(0.8f, 400f)) + fadeOut(tween(150)),
                ) {
                    PollEditor(
                        state.pollQuestion, viewModel::onPollQuestionChange, pollOptions, state.pollMultipleChoice, viewModel::setPollMultipleChoice,
                        Modifier.padding(start = 66.dp, top = 12.dp, end = 16.dp),
                    )
                }
                if (night) {
                    Spacer(Modifier.height(12.dp))
                    NightEditor(state, viewModel, Modifier.padding(start = 66.dp, end = 16.dp), onHelp = { nightHelp = true }) {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.padding(start = 58.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val canAttach = if (night) state.nightImages.size < 4 else state.photos.size < 10 && state.video == null
                    Box {
                        ToolIcon(R.drawable.ic_attach, "Attach photos", if (canAttach) 0.6f else 0.2f) { attachMenu = true }
                        ItdMenu(attachMenu, { attachMenu = false }) {
                            ItdMenuItem("Камера", R.drawable.ic_camera) {
                                attachMenu = false
                                camera.launch(
                                    Intent(context, CameraActivity::class.java)
                                        .putExtra(CameraActivity.VIDEO_ENABLED, videoAllowed)
                                        .putExtra(CameraActivity.PHOTO_PREVIEW, false)
                                        .putExtra(CameraActivity.VIDEO_CAPTION, false),
                                )
                            }
                            HorizontalDivider(Modifier.fillMaxWidth(), color = colors.outline.copy(alpha = 0.15f))
                            ItdMenuItem("Выбрать из галереи", R.drawable.ic_gallery) {
                                attachMenu = false
                                if (canAttach) {
                                    pick.launch(
                                        PickVisualMediaRequest(
                                            if (videoAllowed) ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                            else ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    val canDraw = if (night) state.nightImages.size < 4 else state.photos.size < 10 && state.video == null
                    ToolIcon(R.drawable.ic_palette, "Draw", if (state.video == null && !night) 0.6f else 0.2f) {
                        if (canDraw) {
                            keyboard?.hide()
                            drawing = true
                        }
                    }
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).graphicsLayer { alpha = if (night) 0.3f else 1f }
                            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !night) {
                                viewModel.setPollEnabled(!state.pollEnabled)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_survey), "Create poll", Modifier.size(22.dp),
                            tint = if (state.pollEnabled) colors.primary else colors.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    if (HalloweenPortalManager.actionsAvailable) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (night) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable(remember { MutableInteractionSource() }, indication = null) { viewModel.setNightEnabled(!night) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_moon), "Ночная версия", Modifier.size(22.dp), tint = colors.onSurface.copy(alpha = 0.6f)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            state.hint?.let { hint ->
                Text(
                    hint,
                    Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 24.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = viewModel::dismissHint),
                    color = colors.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
            val sendAlpha by animateFloatAsState(
                when {
                    validation != null -> 0.55f
                    hasContent -> 1f
                    else -> 0.4f
                },
                tween(200), label = "sendAlpha",
            )
            val enabled = (validation != null || hasContent) && !state.isSending && !state.videoBusy
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(colors.primary.copy(alpha = sendAlpha))
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled) {
                            if (validation != null) {
                                viewModel.showHint(validation)
                                return@clickable
                            }
                            editor.hideKeyboard()
                            val (text, spans) = editor.text to editor.spans.toList()
                            viewModel.submit(text, spans, pollOptions.toList())
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isSending) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp)
                    else Text("Опубликовать", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
    if (drawing) {
        Dialog({ drawing = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            DrawingEditor(
                title = "Рисунок", canvasWidth = 1280f, canvasHeight = 800f, saving = false,
                onCancel = { drawing = false },
                onDone = { result ->
                    drawing = false
                    val bitmap = renderDrawing(result.strokes, 1280f, 800f, 1f)
                    val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    bitmap.recycle()
                    val file = File(context.cacheDir, "drawing_${UUID.randomUUID()}.png").apply { writeBytes(png) }
                    viewModel.attachDrawing(file, png, night)
                },
            )
        }
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Ошибка") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
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
private fun ToolIcon(icon: Int, description: String, alpha: Float, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    }
}

@Composable
private fun NightEditor(state: CreatePostState, viewModel: CreatePostViewModel, modifier: Modifier, onHelp: () -> Unit, onPickPhoto: () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF14142B)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌙 Ночная версия", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("что это?", Modifier.clickable(onClick = onHelp), color = Color(0xFFB3AFFA), fontSize = 13.sp)
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
        if (state.nightImages.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.nightImages.forEach { image -> PendingPhotoView(image, { viewModel.remove(image) }, { viewModel.retry(image) }) }
            }
        }
        Text("Фото", Modifier.clickable(onClick = onPickPhoto), color = Color(0xFFB3AFFA))
    }
}
