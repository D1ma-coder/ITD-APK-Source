package com.itd.app.ui.screens.bannerpainter

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.R
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.scale
import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import com.itd.app.data.model.UpdateProfileRequest
import com.itd.app.data.remote.ApiException
import com.itd.app.di.AppContainer
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val PainterPalette = listOf(
    Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308), Color(0xFF22C55E),
    Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF92400E), Color(0xFF6B7280),
)

private val toolLabels = mapOf(
    BannerTool.BRUSH to "Кисть",
    BannerTool.ERASER to "Ластик",
    BannerTool.LINE to "Линия",
    BannerTool.RECTANGLE to "Прямоугольник",
    BannerTool.CIRCLE to "Круг",
)

private val toolIcons = mapOf(
    BannerTool.BRUSH to R.drawable.ic_paintbrush,
    BannerTool.ERASER to R.drawable.ic_eraser,
    BannerTool.LINE to R.drawable.ic_shape_line,
    BannerTool.RECTANGLE to R.drawable.ic_shape_rectangle,
    BannerTool.CIRCLE to R.drawable.ic_shape_circle,
)

fun bannerErrorMessage(error: Exception): String = when ((error as? ApiException)?.code) {
    "FILE_IN_USE" -> "Сначала сними или замени шапку в профиле"
    "BANNER_DRAWING_REQUIRED", "BANNER_DRAWING_FORMAT", "BANNER_DRAWING_INVALID" -> "Не удалось сохранить рисунок. Обнови приложение"
    "BANNER_MODERATION_UNAVAILABLE" -> "Проверка недоступна. Попробуй позже, рисунок не сохранён"
    "BANNER_DRAWING_BUSY" -> "Сервер занят. Попробуй через минуту"
    "BANNER_DRAWING_TOO_COMPLEX", "BANNER_DRAWING_TOO_LARGE" -> "Рисунок слишком сложный. Упрости его и попробуй ещё раз"
    "BANNER_MODERATION_REJECTED" -> "Рисунок не прошёл модерацию"
    else -> "Ошибка сохранения"
}

/** Draws a profile banner, uploads it as a vector drawing and sets it as the profile header. */
@Composable
fun BannerPainterScreen(container: AppContainer, onClose: () -> Unit, onSaved: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    DrawingEditor(
        title = "Баннер",
        canvasWidth = 800f,
        canvasHeight = 267f,
        saving = saving,
        onCancel = onClose,
        onDone = { drawing ->
            saving = true
            scope.launch {
                try {
                    val upload = try {
                        container.postsApi.uploadBannerDrawing(drawing)
                    } catch (e: ApiException) {
                        if (e.code != "BANNER_DRAWING_BUSY") throw e
                        delay((e.retryAfter ?: 2).coerceIn(0, 10) * 1000L)
                        container.postsApi.uploadBannerDrawing(drawing)
                    }
                    container.usersApi.updateProfile(UpdateProfileRequest(bannerId = upload.id))
                    Toast.makeText(context, "Баннер сохранён", Toast.LENGTH_SHORT).show()
                    onSaved(upload.url)
                    onClose()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, bannerErrorMessage(e), Toast.LENGTH_LONG).show()
                } finally {
                    saving = false
                }
            }
        },
    )
}

/** Shared vector drawing editor (profile banner, drawings attached to posts). */
@Composable
fun DrawingEditor(
    title: String,
    canvasWidth: Float,
    canvasHeight: Float,
    saving: Boolean,
    onCancel: () -> Unit,
    onDone: (BannerDrawing) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val strokes = remember { mutableStateListOf<BannerStroke>() }
    val redo = remember { mutableStateListOf<BannerStroke>() }
    var tool by remember { mutableStateOf(BannerTool.BRUSH) }
    var color by remember { mutableStateOf(PainterPalette[0]) }
    var width by remember { mutableStateOf(6f) }
    var current by remember { mutableStateOf<FloatArray?>(null) }
    var showHsv by remember { mutableStateOf(false) }

    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                "Отмена",
                Modifier.align(Alignment.CenterStart).clickable(enabled = !saving, onClick = onCancel),
                color = colors.primary,
                fontSize = 17.sp,
            )
            Text(title, Modifier.align(Alignment.Center), colors.onSurface, 17.sp, fontWeight = FontWeight.SemiBold)
            val canSave = !saving && strokes.isNotEmpty()
            Text(
                if (saving) "Сохранение..." else "Готово",
                Modifier.align(Alignment.CenterEnd).clickable(enabled = canSave) {
                    onDone(BannerDrawing(canvasWidth.toInt(), canvasHeight.toInt(), ArrayList(strokes)))
                },
                color = if (canSave) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.4f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(canvasWidth / canvasHeight)
                    .clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
            ) {
                PainterCanvas(strokes, redo, tool, color, width, current, { current = it }, canvasWidth, canvasHeight)
            }
            Row(
                Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp).clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.85f)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HistoryButton(R.drawable.ic_undo, "Undo", strokes.isNotEmpty()) { redo.add(strokes.removeAt(strokes.lastIndex)) }
                Box(Modifier.size(width = 1.dp, height = 18.dp).background(colors.outlineVariant))
                HistoryButton(R.drawable.ic_redo, "Redo", redo.isNotEmpty()) { strokes.add(redo.removeAt(redo.lastIndex)) }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp).size(44.dp).clip(CircleShape)
                    .background(colors.surfaceVariant.copy(alpha = 0.85f))
                    .clickable(enabled = strokes.isNotEmpty()) { saveToGallery(context, strokes, canvasWidth, canvasHeight) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_download), "Скачать", Modifier.size(22.dp), tint = colors.onSurface)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(toolLabels.getValue(tool), color = colors.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            Slider(value = width, onValueChange = { width = it }, valueRange = 0.25f..30f, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size((width / 30f * 24f).coerceAtLeast(4f).dp).clip(CircleShape).background(if (tool == BannerTool.ERASER) colors.outline else color))
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val custom = color !in PainterPalette
            Box(
                Modifier.size(36.dp).border(3.dp, Brush.sweepGradient(RainbowRing), CircleShape).padding(5.dp).clip(CircleShape)
                    .background(if (custom) color else Color.Red).clickable { showHsv = !showHsv },
            )
            PainterPalette.forEach { swatch ->
                val selected = swatch == color
                Box(
                    Modifier.size(36.dp).let { if (selected) it.border(3.dp, colors.primary, CircleShape).padding(5.dp) else it.padding(1.dp) }
                        .clip(CircleShape).background(swatch).border(1.dp, colors.outlineVariant, CircleShape).clickable { color = swatch },
                )
            }
        }
        if (showHsv) HsvPicker(color) { color = it }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(24.dp)).background(colors.surfaceVariant).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BannerTool.entries.forEach { entry ->
                    PainterToolButton(toolIcons.getValue(entry), toolLabels.getValue(entry), entry == tool, colors.primary.copy(alpha = 0.2f), colors.primary) { tool = entry }
                }
                Box(Modifier.size(width = 1.dp, height = 24.dp).background(colors.outlineVariant))
                PainterToolButton(R.drawable.ic_trash, "Очистить", false, colors.error.copy(alpha = 0.15f), colors.error) {
                    if (strokes.isNotEmpty()) {
                        redo.clear()
                        redo.addAll(strokes.reversed())
                        strokes.clear()
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private val RainbowRing = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

@Composable
private fun HistoryButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Icon(
        painterResource(icon),
        description,
        Modifier.size(32.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).padding(5.dp),
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
    )
}

@Composable
private fun PainterToolButton(icon: Int, description: String, selected: Boolean, selectedBackground: Color, selectedTint: Color, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) selectedBackground else Color.Transparent, spring(stiffness = 400f), label = "toolBg")
    val tint by animateColorAsState(if (selected) selectedTint else MaterialTheme.colorScheme.onSurface, spring(stiffness = 400f), label = "toolTint")
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = 0.45f, stiffness = 1500f), label = "toolIconScale")
    Box(Modifier.size(44.dp).clip(CircleShape).background(background).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), description, Modifier.size(20.dp).scale(scale), tint = tint)
    }
}

@Composable
private fun PainterCanvas(
    strokes: MutableList<BannerStroke>,
    redo: MutableList<BannerStroke>,
    tool: BannerTool,
    color: Color,
    width: Float,
    current: FloatArray?,
    onCurrent: (FloatArray?) -> Unit,
    canvasWidth: Float,
    canvasHeight: Float,
) {
    Canvas(
        Modifier.fillMaxSize()
            .pointerInput(tool, color, width) {
                val scale = canvasWidth / size.width
                fun point(offset: Offset) = floatArrayOf(offset.x * scale, offset.y * scale)
                detectTapGestures { offset ->
                    if (tool.freehand) {
                        strokes.add(BannerStroke(tool, color.toArgb(), width, point(offset)))
                        redo.clear()
                    }
                }
            }
            .pointerInput(tool, color, width) {
                val scale = canvasWidth / size.width
                var points: FloatArray? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        points = floatArrayOf(offset.x * scale, offset.y * scale)
                        onCurrent(points)
                    },
                    onDrag = { change, _ ->
                        val existing = points ?: return@detectDragGestures
                        val x = change.position.x * scale
                        val y = change.position.y * scale
                        points = if (tool.freehand) {
                            val lastX = existing[existing.size - 2]
                            val lastY = existing[existing.size - 1]
                            if (hypot(x - lastX, y - lastY) < 1.5f) existing else existing + floatArrayOf(x, y)
                        } else {
                            floatArrayOf(existing[0], existing[1], x, y)
                        }
                        onCurrent(points)
                    },
                    onDragEnd = {
                        points?.let { finished ->
                            val finalPoints = if (!tool.freehand && finished.size == 2) finished + finished else finished
                            strokes.add(BannerStroke(tool, color.toArgb(), width, finalPoints))
                            redo.clear()
                        }
                        points = null
                        onCurrent(null)
                    },
                    onDragCancel = {
                        points = null
                        onCurrent(null)
                    },
                )
            },
    ) {
        val scale = size.width / canvasWidth
        strokes.forEach { drawStroke(it.tool, Color(it.color), it.width, it.points, scale) }
        current?.let { points ->
            if (tool.freehand || points.size == 4) drawStroke(tool, color, width, points, scale)
        }
    }
}

@Composable
private fun HsvPicker(color: Color, onChange: (Color) -> Unit) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var hue by remember(color) { mutableStateOf(hsv[0]) }
    var saturation by remember(color) { mutableStateOf(hsv[1]) }
    var value by remember(color) { mutableStateOf(hsv[2]) }
    fun emit() = onChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))))
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text("Оттенок", fontSize = 12.sp)
        Slider(hue, { hue = it; emit() }, valueRange = 0f..360f)
        Text("Насыщенность", fontSize = 12.sp)
        Slider(saturation, { saturation = it; emit() })
        Text("Яркость", fontSize = 12.sp)
        Slider(value, { value = it; emit() })
    }
}

private fun DrawScope.drawStroke(tool: BannerTool, color: Color, width: Float, points: FloatArray, scale: Float) {
    val paintColor = if (tool == BannerTool.ERASER) Color.White else color
    val style = Stroke(width = width * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (tool) {
        BannerTool.BRUSH, BannerTool.ERASER -> {
            if (points.size == 2) {
                drawCircle(paintColor, width * scale / 2, Offset(points[0] * scale, points[1] * scale))
                return
            }
            val path = Path().apply {
                moveTo(points[0] * scale, points[1] * scale)
                for (i in 2 until points.size step 2) lineTo(points[i] * scale, points[i + 1] * scale)
            }
            drawPath(path, paintColor, style = style)
        }
        BannerTool.LINE -> drawLine(paintColor, Offset(points[0] * scale, points[1] * scale), Offset(points[2] * scale, points[3] * scale), width * scale, StrokeCap.Round)
        BannerTool.RECTANGLE -> {
            val left = minOf(points[0], points[2]) * scale
            val top = minOf(points[1], points[3]) * scale
            drawRect(paintColor, Offset(left, top), Size(kotlin.math.abs(points[2] - points[0]) * scale, kotlin.math.abs(points[3] - points[1]) * scale), style = style)
        }
        BannerTool.CIRCLE -> {
            val radius = hypot(points[2] - points[0], points[3] - points[1]) * scale
            drawCircle(paintColor, radius, Offset(points[0] * scale, points[1] * scale), style = style)
        }
    }
}

private fun saveToGallery(context: Context, strokes: List<BannerStroke>, width: Float, height: Float) {
    val bitmap = Bitmap.createBitmap(width.toInt() * 2, height.toInt() * 2, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.scale(2f, 2f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        paint.color = if (stroke.tool == BannerTool.ERASER) android.graphics.Color.WHITE else stroke.color
        paint.strokeWidth = stroke.width
        val p = stroke.points
        when (stroke.tool) {
            BannerTool.BRUSH, BannerTool.ERASER -> {
                if (p.size == 2) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(p[0], p[1], stroke.width / 2, paint)
                    paint.style = Paint.Style.STROKE
                } else {
                    val path = android.graphics.Path().apply {
                        moveTo(p[0], p[1])
                        for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
                    }
                    canvas.drawPath(path, paint)
                }
            }
            BannerTool.LINE -> canvas.drawLine(p[0], p[1], p[2], p[3], paint)
            BannerTool.RECTANGLE -> canvas.drawRect(minOf(p[0], p[2]), minOf(p[1], p[3]), maxOf(p[0], p[2]), maxOf(p[1], p[3]), paint)
            BannerTool.CIRCLE -> canvas.drawCircle(p[0], p[1], hypot(p[2] - p[0], p[3] - p[1]), paint)
        }
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "itd-drawing-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ITD")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    val saved = uri != null && context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
    Toast.makeText(context, if (saved) "Сохранено в галерею" else "Не удалось сохранить", Toast.LENGTH_SHORT).show()
}
