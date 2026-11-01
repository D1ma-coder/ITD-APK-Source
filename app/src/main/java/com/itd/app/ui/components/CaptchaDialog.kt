package com.itd.app.ui.components

import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.itd.app.data.model.CaptchaAnswer
import com.itd.app.data.model.CaptchaPage
import org.json.JSONObject

@Composable
fun CaptchaDialog(page: CaptchaPage, onAnswer: (CaptchaAnswer) -> Unit, onDismiss: () -> Unit, action: String = "login") {
    val callback by rememberUpdatedState(onAnswer)
    val theme = if (isSystemInDarkTheme()) "dark" else "light"
    val url = remember(page.url, theme, action) {
        Uri.parse(page.url).buildUpon().appendQueryParameter("theme", theme)
            .apply { if (action.isNotEmpty()) appendQueryParameter("action", action) }.build().toString()
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose { webView?.apply { removeJavascriptInterface("AndroidInterface"); destroy() } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтвердите, что вы не робот") },
        text = {
            AndroidView(
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(false)
                        isVerticalScrollBarEnabled = false
                        webViewClient = WebViewClient()
                        addJavascriptInterface(CaptchaBridge(page) { callback(it) }, "AndroidInterface")
                        loadUrl(url)
                        webView = this
                    }
                },
            )
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private class CaptchaBridge(private val page: CaptchaPage, private val onAnswer: (CaptchaAnswer) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun sendToken(message: String) {
        val payload = runCatching { JSONObject(message) }.getOrNull() ?: return
        if (payload.optString("type") != "token") return
        val token = payload.optString("token").takeIf { it.isNotEmpty() } ?: return
        handler.post { onAnswer(CaptchaAnswer(token, page.field)) }
    }

    @JavascriptInterface
    fun onError(message: String) = Unit
}
