package com.itd.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.text.style.UpdateAppearance
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.itd.app.data.model.PostSpan
import java.lang.ref.WeakReference

/** Spoiler in the editor: a grey highlight behind the text. */
class SpoilerSpan : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(paint: TextPaint) {
        paint.bgColor = AndroidColor.argb(64, 128, 128, 128)
    }
}

/** Link in the editor: blue text that remembers its URL. */
class LinkSpan(val url: String) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(paint: TextPaint) {
        paint.color = COLOR
    }

    companion object {
        val COLOR = AndroidColor.rgb(19, 129, 213)
    }
}

/**
 * Post text field with formatting from the selection menu: spoiler, bold, italic, monospace,
 * strikethrough, underline, link and plain.
 */
@SuppressLint("AppCompatCustomView", "ViewConstructor")
class RichEditText(context: Context) : EditText(context, null, android.R.attr.editTextStyle) {
    var onSpansChanged: (() -> Unit)? = null
    /** (start, end, current URL) when "Ссылка" is chosen for a selection. */
    var onLinkRequested: ((Int, Int, String?) -> Unit)? = null

    /** Removes [spans] from start..end, keeping their parts outside the range. */
    fun removeSpans(start: Int, end: Int, spans: List<Any>) {
        val text = text ?: return
        for (span in spans) {
            val spanStart = text.getSpanStart(span)
            val spanEnd = text.getSpanEnd(span)
            text.removeSpan(span)
            if (spanStart < start) text.setSpan(span, spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (spanEnd > end) {
                val tail: Any = when (span) {
                    is StyleSpan -> StyleSpan(span.style)
                    is UnderlineSpan -> UnderlineSpan()
                    is StrikethroughSpan -> StrikethroughSpan()
                    is TypefaceSpan -> TypefaceSpan(span.family ?: "monospace")
                    is SpoilerSpan -> SpoilerSpan()
                    is LinkSpan -> LinkSpan(span.url)
                    else -> span
                }
                text.setSpan(tail, end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    /** Whether [spans] together cover every character of start..end. */
    fun covers(start: Int, end: Int, spans: List<Any>): Boolean {
        if (spans.isEmpty()) return false
        val text = text ?: return false
        var position = start
        while (position < end) {
            val span = spans.firstOrNull { text.getSpanStart(it) <= position && text.getSpanEnd(it) > position } ?: return false
            position = text.getSpanEnd(span)
        }
        return true
    }

    /** Applies [span] to start..end, or removes it where the selection already has it everywhere. */
    fun toggle(start: Int, end: Int, existing: List<Any>, span: () -> Any) {
        if (covers(start, end, existing)) removeSpans(start, end, existing)
        else text?.setSpan(span(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun toggleStyle(start: Int, end: Int, style: Int) {
        val text = text ?: return
        toggle(start, end, text.getSpans(start, end, StyleSpan::class.java).filter { it.style == style }) { StyleSpan(style) }
    }

    override fun onDetachedFromWindow() {
        if (hasFocus()) hideKeyboard()
        super.onDetachedFromWindow()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!focused && windowToken != null) hideKeyboard()
    }

    private fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
        super.startActionMode(FloatingCallback(FormattingCallback(callback), callback))

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
        super.startActionMode(FloatingCallback(FormattingCallback(callback), callback), type)

    /** Adds the formatting items to the system selection menu. */
    private inner class FormattingCallback(private val system: ActionMode.Callback?) : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val result = system?.onCreateActionMode(mode, menu) ?: true
            if (menu.findItem(BOLD) != null) return result
            val size = menu.size()
            menu.add(GROUP, SPOILER, size + 1, "Спойлер")
            menu.add(GROUP, BOLD, size + 2, styled("Жирный", StyleSpan(android.graphics.Typeface.BOLD)))
            menu.add(GROUP, ITALIC, size + 3, styled("Курсив", StyleSpan(android.graphics.Typeface.ITALIC)))
            menu.add(GROUP, MONOSPACE, size + 4, SpannableString("Моноширинный").apply { setSpan(TypefaceSpan("monospace"), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) })
            menu.add(GROUP, STRIKE, size + 5, SpannableString("Зачёркнутый").apply { setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) })
            menu.add(GROUP, UNDERLINE, size + 6, SpannableString("Подчёркнутый").apply { setSpan(UnderlineSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) })
            menu.add(GROUP, LINK, size + 7, "Ссылка")
            menu.add(GROUP, PLAIN, size + 8, "Обычный")
            return result
        }

        private fun styled(text: String, span: Any) = SpannableStringBuilder(text).apply { setSpan(span, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = system?.onPrepareActionMode(mode, menu) ?: false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val start = selectionStart
            val end = selectionEnd
            if (start < 0 || end <= start) {
                return try {
                    system?.onActionItemClicked(mode, item) ?: false
                } catch (_: Throwable) {
                    false
                }
            }
            val text = text
            when (item.itemId) {
                SPOILER -> if (text != null) toggle(start, end, text.getSpans(start, end, SpoilerSpan::class.java).toList()) { SpoilerSpan() }
                BOLD -> toggleStyle(start, end, android.graphics.Typeface.BOLD)
                ITALIC -> toggleStyle(start, end, android.graphics.Typeface.ITALIC)
                MONOSPACE -> if (text != null) {
                    toggle(start, end, text.getSpans(start, end, TypefaceSpan::class.java).filter { it.family == "monospace" }) { TypefaceSpan("monospace") }
                }
                STRIKE -> if (text != null) toggle(start, end, text.getSpans(start, end, StrikethroughSpan::class.java).toList()) { StrikethroughSpan() }
                UNDERLINE -> if (text != null) toggle(start, end, text.getSpans(start, end, UnderlineSpan::class.java).toList()) { UnderlineSpan() }
                LINK -> {
                    val current = text?.getSpans(start, end, LinkSpan::class.java)?.firstOrNull()?.url
                    onLinkRequested?.invoke(start, end, current)
                    mode.finish()
                    return true
                }
                PLAIN -> if (text != null) {
                    listOf(StyleSpan::class.java, UnderlineSpan::class.java, StrikethroughSpan::class.java, TypefaceSpan::class.java, SpoilerSpan::class.java, LinkSpan::class.java)
                        .forEach { type -> removeSpans(start, end, text.getSpans(start, end, type).toList()) }
                }
            }
            onSpansChanged?.invoke()
            mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            system?.onDestroyActionMode(mode)
        }
    }

    /** Keeps the floating toolbar positioned where the system callback wants it. */
    private class FloatingCallback(private val formatting: ActionMode.Callback, private val system: ActionMode.Callback?) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = formatting.onCreateActionMode(mode, menu)
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = formatting.onPrepareActionMode(mode, menu)
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = formatting.onActionItemClicked(mode, item)
        override fun onDestroyActionMode(mode: ActionMode) = formatting.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (system is ActionMode.Callback2) system.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }
    }

    companion object {
        private const val GROUP = -268431360
        private const val SPOILER = -268431359
        private const val BOLD = -268431358
        private const val ITALIC = -268431357
        private const val MONOSPACE = -268431356
        private const val STRIKE = -268431355
        private const val UNDERLINE = -268431354
        private const val LINK = -268431353
        private const val PLAIN = -268431352

        fun span(type: String, text: Editable, style: Any): PostSpan {
            val start = text.getSpanStart(style)
            return PostSpan(type, text.getSpanEnd(style) - start, start)
        }
    }
}

/** Text and formatting of a [RichTextEditor], readable from Compose. */
class RichTextState {
    private val textState = mutableStateOf("")
    val text: String get() = textState.value
    val spans = mutableStateListOf<PostSpan>()
    internal var view: WeakReference<RichEditText>? = null
    private var pending: Pair<String, List<PostSpan>>? = null

    /** Replaces the content (a draft or an edited post); applied once the field exists. */
    fun setContent(text: String, spans: List<PostSpan>) {
        pending = text to spans
        view?.get()?.let(::apply)
    }

    internal fun apply(field: RichEditText) {
        val (content, styles) = pending ?: return
        field.setText(content)
        field.text?.let { text ->
            val length = text.length
            for (span in styles) {
                val start = span.offset.coerceIn(0, length)
                val end = (span.offset + span.length).coerceIn(0, length)
                if (start >= end) continue
                val style: Any = when (span.type) {
                    "spoiler" -> SpoilerSpan()
                    "monospace" -> TypefaceSpan("monospace")
                    "italic" -> StyleSpan(android.graphics.Typeface.ITALIC)
                    "underline" -> UnderlineSpan()
                    "strikethrough", "strike" -> StrikethroughSpan()
                    "bold" -> StyleSpan(android.graphics.Typeface.BOLD)
                    "link" -> span.url?.let(::LinkSpan)
                    else -> null
                } ?: continue
                text.setSpan(style, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            field.onSpansChanged?.invoke()
        }
        pending = null
        val current = field.text?.toString() ?: ""
        if (text != current) textState.value = current
        readSpans()
    }

    internal fun onTextChanged(value: String) {
        if (text != value) textState.value = value
        readSpans()
    }

    /** Rebuilds [spans] from the field, sorted by offset. */
    fun readSpans() {
        val field = view?.get() ?: return
        val text = field.text
        val result = if (text == null) {
            emptyList()
        } else {
            val list = ArrayList<PostSpan>()
            val length = text.length
            for (style in text.getSpans(0, length, StyleSpan::class.java)) {
                when (style.style) {
                    android.graphics.Typeface.BOLD -> list += RichEditText.span("bold", text, style)
                    android.graphics.Typeface.ITALIC -> list += RichEditText.span("italic", text, style)
                    android.graphics.Typeface.BOLD_ITALIC -> {
                        list += RichEditText.span("bold", text, style)
                        list += RichEditText.span("italic", text, style)
                    }
                }
            }
            text.getSpans(0, length, UnderlineSpan::class.java).forEach { list += RichEditText.span("underline", text, it) }
            text.getSpans(0, length, StrikethroughSpan::class.java).forEach { list += RichEditText.span("strike", text, it) }
            text.getSpans(0, length, TypefaceSpan::class.java).filter { it.family == "monospace" }
                .forEach { list += RichEditText.span("monospace", text, it) }
            text.getSpans(0, length, SpoilerSpan::class.java).forEach { list += RichEditText.span("spoiler", text, it) }
            for (link in text.getSpans(0, length, LinkSpan::class.java)) {
                val start = text.getSpanStart(link)
                val end = text.getSpanEnd(link)
                if (start in 0 until end) list += PostSpan("link", end - start, start, link.url)
            }
            list.sortedBy { it.offset }
        }
        spans.clear()
        spans.addAll(result)
    }

    fun hideKeyboard() {
        val field = view?.get() ?: return
        (field.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(field.windowToken, 0)
        field.clearFocus()
    }

    fun focus() {
        val field = view?.get() ?: return
        field.requestFocus()
        field.post {
            (field.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Text without surrounding blanks, with the spans shifted and clipped to it. */
    fun trimmed(): Pair<String, List<PostSpan>> = trimmedContent(text, spans.toList())
}

fun trimmedContent(text: String, spans: List<PostSpan>): Pair<String, List<PostSpan>> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return trimmed to emptyList()
    if (trimmed == text || spans.isEmpty()) return trimmed to spans
    val shift = text.indexOf(trimmed).coerceAtLeast(0)
    val length = trimmed.length
    val result = spans.mapNotNull { span ->
        val start = (span.offset - shift).coerceAtLeast(0)
        val end = (span.offset + span.length - shift).coerceAtMost(length)
        if (end <= start) null else span.copy(offset = start, length = end - start)
    }
    return trimmed to result
}

private data class PendingLinkRequest(val start: Int, val end: Int, val initialUrl: String?)

/** Multi-line text field with formatting; the placeholder shows while it is empty. */
@Composable
fun RichTextEditor(
    state: RichTextState,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    placeholder: (@Composable () -> Unit)? = null,
    maxLines: Int? = null,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val cursor = MaterialTheme.colorScheme.primary.toArgb()
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f).toArgb()
    var linkRequest by remember { mutableStateOf<PendingLinkRequest?>(null) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        AndroidView(
            factory = { context ->
                RichEditText(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    background = null
                    setPadding(0, 0, 0, 0)
                    gravity = Gravity.TOP or Gravity.START
                    movementMethod = LinkMovementMethod.getInstance()
                    setHorizontallyScrolling(false)
                    isVerticalScrollBarEnabled = false
                    maxLines?.let { this.maxLines = it }
                    highlightColor = highlight
                    if (Build.VERSION.SDK_INT >= 29) {
                        try {
                            textCursorDrawable = GradientDrawable().apply {
                                setColor(cursor)
                                setSize((resources.displayMetrics.density * 2f).toInt(), 0)
                            }
                        } catch (_: Throwable) {
                        }
                    }
                    val fontSize = textStyle.fontSize
                    if (fontSize.isSpecified) textSize = fontSize.value
                    setTextColor((if (textStyle.color.isSpecified) textStyle.color else onSurface).toArgb())
                    val lineHeight = textStyle.lineHeight
                    if (lineHeight.isSpecified && fontSize.isSpecified) setLineSpacing(0f, (lineHeight.value / fontSize.value).coerceAtLeast(1f))
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) = state.onTextChanged(s?.toString() ?: "")
                    })
                    onSpansChanged = state::readSpans
                    onLinkRequested = { start, end, url -> linkRequest = PendingLinkRequest(start, end, url) }
                    state.view = WeakReference(this)
                    state.apply(this)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.text.isEmpty() && placeholder != null) {
            Box(Modifier.align(Alignment.TopStart)) { placeholder() }
        }
    }
    linkRequest?.let { request ->
        LinkDialog(request.initialUrl, onDismiss = { linkRequest = null }) { url ->
            val field = state.view?.get()
            val text = field?.text
            if (field != null && text != null && request.start >= 0 && request.end > request.start && request.end <= text.length) {
                field.removeSpans(request.start, request.end, text.getSpans(request.start, request.end, LinkSpan::class.java).toList())
                text.setSpan(LinkSpan(url), request.start, request.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                field.onSpansChanged?.invoke()
            }
            linkRequest = null
        }
    }
}

/** Dims and blurs what is behind the dialog this is called from (blur on Android 12+). */
@Composable
fun DialogBlurBehind() {
    val view = LocalView.current
    val density = LocalDensity.current
    SideEffect {
        var parent = view.parent
        while (parent != null) {
            if (parent is DialogWindowProvider) {
                val window = parent.window
                window.setDimAmount(0.32f)
                if (Build.VERSION.SDK_INT >= 31) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply { blurBehindRadius = with(density) { 28.dp.toPx() }.toInt() }
                }
                return@SideEffect
            }
            parent = parent.parent
        }
    }
}

/** 44dp pill button of the app's dialogs; [primary] is the filled one. */
@Composable
fun DialogButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val background = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.height(44.dp).clip(CircleShape).background(background)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RowScope.dialogButton(text: String, primary: Boolean, onClick: () -> Unit) =
    DialogButton(text, onClick, Modifier.weight(1f), primary)

/** "Ссылка" dialog of the editor: URL field, paste suggestion from the clipboard, Отмена / ОК. */
@Composable
fun LinkDialog(initialUrl: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val initial = initialUrl ?: ""
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    fun submit() {
        val url = value.text.trim()
        if (url.isEmpty()) {
            onDismiss()
            return
        }
        onConfirm(if (url.contains("://")) url else "https://$url")
    }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        DialogBlurBehind()
        Box(
            Modifier.padding(horizontal = 32.dp).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).glassRim(28.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ссылка", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Введите URL", Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (value.text.isEmpty()) {
                        Text("https://example.com", Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    BasicTextField(
                        value, { value = it }, Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    )
                }
                val copied = clipboard.getText()?.text
                if (copied != null && copied.isNotBlank() && (value.text.isBlank() || value.text == initial)) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Вставить «" + copied.take(40) + (if (copied.length > 40) "…" else "") + "»",
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable(remember { MutableInteractionSource() }, indication = null) { value = TextFieldValue(copied, TextRange(copied.length)) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    dialogButton("Отмена", false, onDismiss)
                    dialogButton("ОК", true, ::submit)
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
