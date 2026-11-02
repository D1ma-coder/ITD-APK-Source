package com.itd.app.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.itd.app.data.model.PostSpan

@Composable
fun PostText(
    content: String,
    spans: List<PostSpan>,
    modifier: Modifier = Modifier,
    onHashtag: ((String) -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE,
    onOverflow: (Boolean) -> Unit = {},
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    var link by remember { mutableStateOf<String?>(null) }
    link?.let { url -> LinkSheet(url) { link = null } }
    val uriHandler = LocalUriHandler.current
    var revealed by remember(content, spans) { mutableStateOf(emptySet<Int>()) }
    val colors = MaterialTheme.colorScheme
    val text = remember(content, spans, revealed, colors) {
        buildAnnotatedString {
            append(content)
            spans.forEach { span ->
                val start = span.offset.coerceIn(0, content.length)
                val end = (span.offset.toLong() + span.length).coerceIn(0, content.length.toLong()).toInt()
                if (start >= end) return@forEach
                val style = when (span.type) {
                    "bold" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "italic" -> SpanStyle(fontStyle = FontStyle.Italic)
                    "underline" -> SpanStyle(textDecoration = TextDecoration.Underline)
                    "strike", "strikethrough" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    "monospace" -> SpanStyle(fontFamily = FontFamily.Monospace, letterSpacing = .5.sp)
                    "link", "mention", "hashtag" -> SpanStyle(color = colors.primary)
                    "spoiler" -> if (span.offset !in revealed) SpanStyle(color = Color.Transparent, background = colors.onSurfaceVariant) else SpanStyle()
                    else -> SpanStyle()
                }
                addStyle(style, start, end)
                when (span.type) {
                    "spoiler" -> if (span.offset !in revealed) addStringAnnotation("spoiler", span.offset.toString(), start, end)
                    "link" -> span.url?.let { addStringAnnotation("link", it, start, end) }
                    "mention" -> span.username?.let { addStringAnnotation("mention", it, start, end) }
                    "hashtag" -> addStringAnnotation("hashtag", span.tag ?: content.substring(start, end).removePrefix("#"), start, end)
                }
            }
        }
    }
    ClickableText(
        text,
        modifier,
        style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, fontSize = fontSize, lineHeight = lineHeight),
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = { onOverflow(it.hasVisualOverflow) },
    ) { offset ->
        val annotations = text.getStringAnnotations(offset, offset)
        val spoiler = annotations.firstOrNull { it.tag == "spoiler" }
        if (spoiler != null) revealed = revealed + spoiler.item.toInt()
        else annotations.firstOrNull()?.let { annotation ->
            when (annotation.tag) {
                "hashtag" -> onHashtag?.invoke(annotation.item)
                "mention" -> uriHandler.openUri("itd://profile/${Uri.encode(annotation.item)}")
                "link" -> if (Uri.parse(annotation.item).scheme?.lowercase() in setOf("http", "https")) link = annotation.item
            }
        }
    }
}

/** Actions for a link tapped inside a post. */
@Composable
fun LinkSheet(url: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = LocalUriHandler.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ссылка") },
        text = {
            Column {
                Text(url, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 3)
                Text("Открыть", Modifier.fillMaxWidth().clickable { onDismiss(); runCatching { uriHandler.openUri(url) } }.padding(vertical = 12.dp))
                Text("Скопировать", Modifier.fillMaxWidth().clickable { onDismiss(); copyLink(context, url) }.padding(vertical = 12.dp))
                Text("Поделиться", Modifier.fillMaxWidth().clickable {
                    onDismiss()
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, url)
                    context.startActivity(android.content.Intent.createChooser(intent, "Поделиться"))
                }.padding(vertical = 12.dp))
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
