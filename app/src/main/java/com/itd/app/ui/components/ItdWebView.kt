package com.itd.app.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.itd.app.data.local.SessionStorage
import com.itd.app.data.remote.ApiClient
import com.itd.app.util.LogTag
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object TrustedUrls {
    private val hosts = setOf("xn--d1ah4a.com")

    fun isTrusted(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return hosts.any { host == it || host.endsWith(".$it") }
    }
}

/** Exposes the session to ITD web pages (shop, portal, events) as `window.AndroidBridge`. */
class AndroidBridge(
    private val webView: WebView,
    private val session: SessionStorage,
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val handler: Handler,
    private val canClose: Boolean,
    private val onOwnsClose: () -> Unit,
    private val onClose: () -> Unit,
) {
    fun sendAuth(token: String?) {
        val script = "window.setNativeAuth && window.setNativeAuth({token:'${token.orEmpty()}',platform:'android',version:'1.1.0'," +
            "deviceId:'${session.deviceId}'${if (canClose) ",canClose:true" else ""}});"
        handler.post { webView.evaluateJavascript(script, null) }
    }

    @JavascriptInterface
    fun requestAuth() = sendAuth(session.accessToken)

    @JavascriptInterface
    fun onTokenExpired() {
        scope.launch {
            val refreshed = runCatching { api.refreshSession() }.getOrDefault(false)
            sendAuth(if (refreshed) session.accessToken else null)
        }
    }

    @JavascriptInterface
    fun ownsClose() {
        if (canClose) handler.post { if (TrustedUrls.isTrusted(webView.url)) onOwnsClose() }
    }

    @JavascriptInterface
    fun closeEvent() {
        if (canClose) handler.post(onClose)
    }
}

/** An ITD web page with native auth; untrusted links open in the browser. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ItdWebPage(
    url: String,
    session: SessionStorage,
    api: ApiClient,
    modifier: Modifier = Modifier,
    canClose: Boolean = false,
    onClose: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageOwnsClose by remember { mutableStateOf(false) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    fun deliver(uris: List<Uri>) {
        fileCallback?.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
        fileCallback = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { deliver(listOfNotNull(it)) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { deliver(it) }
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { deliver(listOfNotNull(it)) }
    val pickDocuments = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { deliver(it) }
    val imageTypes = setOf(".jpg", ".jpeg", ".png", ".webp", ".avif", ".gif", ".heic", ".heif")

    BackHandler(enabled = canGoBack || (canClose && !pageOwnsClose)) {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }
    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val handler = Handler(Looper.getMainLooper())
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    addJavascriptInterface(
                        AndroidBridge(this, session, api, scope, handler, canClose, { pageOwnsClose = true }, onClose),
                        "AndroidBridge",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val target = request?.url ?: return false
                            if (TrustedUrls.isTrusted(target.toString())) return false
                            runCatching {
                                view?.context?.startActivity(
                                    Intent(Intent.ACTION_VIEW, target).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                            return true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                            if (callback == null) return false
                            fileCallback?.onReceiveValue(null)
                            fileCallback = callback
                            val accept = params?.acceptTypes.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
                            val imagesOnly = accept.isEmpty() || accept.all { it.lowercase(Locale.ROOT).startsWith("image/") || it.lowercase(Locale.ROOT) in imageTypes }
                            val multiple = params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            when {
                                imagesOnly && multiple -> pickImages.launch(request)
                                imagesOnly -> pickImage.launch(request)
                                multiple -> pickDocuments.launch("*/*")
                                else -> pickDocument.launch("*/*")
                            }
                            return true
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    if (TrustedUrls.isTrusted(url)) loadUrl(url) else LogTag.UI.tree().w("blocked untrusted url %s", url)
                    webView = this
                }
            },
        )
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
    DisposableEffect(Unit) {
        onDispose {
            fileCallback?.onReceiveValue(null)
            webView?.destroy()
        }
    }
}
