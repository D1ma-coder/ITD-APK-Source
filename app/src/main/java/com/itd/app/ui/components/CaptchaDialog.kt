package com.itd.app.ui.components

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.itd.app.data.model.CaptchaAnswer
import com.itd.app.data.model.CaptchaPage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import org.json.JSONObject

private const val WIDGET_WIDTH = 300f
private const val WIDGET_HEIGHT = 74f

/** Cloudflare Turnstile widget floating over a dimmed screen. */
@Composable
fun CaptchaDialog(page: CaptchaPage, onAnswer: (CaptchaAnswer) -> Unit, onDismiss: () -> Unit, action: String = "login") {
    val callback by rememberUpdatedState(onAnswer)
    val dismiss by rememberUpdatedState(onDismiss)
    val handler = remember { Handler(Looper.getMainLooper()) }
    val theme = if (isSystemInDarkTheme()) "dark" else "light"
    val url = remember(page.url, theme) {
        val separator = if ('?' in page.url) "&" else "?"
        buildString {
            append(page.url).append(separator).append("theme=").append(theme)
            if (action.isNotEmpty()) append("&action=").append(action)
        }
    }
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scrimAlpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.85f) }
    val alpha = remember { Animatable(0f) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        launch { scrimAlpha.animateTo(1f, tween(300, easing = LinearOutSlowInEasing)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 1500f)) }
        launch { alpha.animateTo(1f, tween(200)) }
    }
    DisposableEffect(Unit) {
        onDispose { webView?.apply { removeJavascriptInterface("AndroidInterface"); destroy() } }
    }

    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            animateOut(scrimAlpha, scale, alpha)
            then()
        }
    }

    Dialog(
        onDismissRequest = { close { dismiss() } },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { this.alpha = scrimAlpha.value }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, null) { close { dismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    }
                    .size(WIDGET_WIDTH.dp, 90.dp)
                    .clickable(remember { MutableInteractionSource() }, null) {},
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(!loaded, enter = fadeIn(tween(150)), exit = fadeOut(tween(200))) { DotsLoader() }
                AndroidView(
                    modifier = Modifier.size(WIDGET_WIDTH.dp, WIDGET_HEIGHT.dp).graphicsLayer { this.alpha = if (loaded) 1f else 0f },
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportZoom(false)
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            overScrollMode = WebView.OVER_SCROLL_NEVER
                            setBackgroundColor(0)
                            addJavascriptInterface(
                                CaptchaBridge(handler, page) { answer -> close { callback(answer) } },
                                "AndroidInterface",
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    loaded = true
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val target = request?.url?.toString() ?: return false
                                    return !TrustedUrls.isTrusted(target)
                                }
                            }
                            loadUrl(url)
                            webView = this
                        }
                    },
                )
            }
        }
    }
}

private suspend fun animateOut(scrimAlpha: Animatable<Float, *>, scale: Animatable<Float, *>, alpha: Animatable<Float, *>) {
    coroutineScope {
        listOf(
            launch { scrimAlpha.animateTo(0f, tween(200)) },
            launch { scale.animateTo(0.85f, tween(200)) },
            launch { alpha.animateTo(0f, tween(150)) },
        ).joinAll()
    }
}

@Composable
private fun DotsLoader() {
    val transition = rememberInfiniteTransition(label = "dotsLoader")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val spec = infiniteRepeatable<Float>(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(index * 150))
            val dotAlpha by transition.animateFloat(0.3f, 1f, spec, label = "dotAlpha$index")
            val dotScale by transition.animateFloat(0.8f, 1.2f, spec, label = "dotScale$index")
            Box(
                Modifier.graphicsLayer {
                    alpha = dotAlpha
                    scaleX = dotScale
                    scaleY = dotScale
                }.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private class CaptchaBridge(private val handler: Handler, private val page: CaptchaPage, private val onAnswer: (CaptchaAnswer) -> Unit) {
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
