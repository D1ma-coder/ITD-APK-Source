package com.itd.app.data.remote

import android.os.Build
import com.itd.app.data.local.SessionStorage
import com.itd.app.data.model.ApiErrorResponse
import com.itd.app.data.model.LoginResponse
import com.itd.app.util.Diagnostics
import com.itd.app.util.LogTag
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * HTTP access to the ITD API. Authorization, token refresh and common headers live in OkHttp
 * interceptors so that every consumer of [okHttpClient] (Ktor, the notification stream) shares them.
 */
class ApiClient(
    private val session: SessionStorage,
    private val baseUrl: String = "https://xn--d1ah4a.com",
    private val userAgent: String = "ITD/1.1.0 (Android; ${Build.VERSION.RELEASE}; ${Build.MODEL})",
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val refreshLock = Any()
    var onSessionExpired: (() -> Unit)? = null

    val cookies = RefreshTokenCookieStorage(session, Url(baseUrl).host)

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = session.accessToken
        val request = token?.let { original.newBuilder().header("Authorization", "Bearer $it").build() } ?: original
        val response = chain.proceed(request)
        if (response.code != 401 || original.url.encodedPath.contains("/auth/")) return@Interceptor response
        LogTag.AUTH.tree().d("401 on %s, refreshing", original.url.encodedPath)
        response.close()
        val refreshed = synchronized(refreshLock) {
            val current = session.accessToken
            if (current == null || current == token) refreshBlocking(chain) else current
        }
        if (refreshed == null) return@Interceptor chain.proceed(request)
        chain.proceed(original.newBuilder().header("Authorization", "Bearer $refreshed").build())
    }

    private val headersInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("X-Device-Id", session.deviceId)
                .header("X-Platform", "android")
                .header("X-App-Version", "1.1.0")
                .header("User-Agent", userAgent)
                .build(),
        )
    }

    private val loggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        val startedAt = System.nanoTime()
        try {
            chain.proceed(request).also { response ->
                Diagnostics.network(
                    request.method,
                    request.url.toString(),
                    response.code,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    response.body?.contentLength()?.coerceAtLeast(0) ?: 0,
                )
                LogTag.NETWORK.tree().d(
                    "%s %s -> %d (%d ms, %d bytes)",
                    request.method,
                    request.url.encodedPath,
                    response.code,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    response.body?.contentLength()?.coerceAtLeast(0) ?: 0,
                )
            }
        } catch (e: IOException) {
            Diagnostics.network(request.method, request.url.toString(), null, (System.nanoTime() - startedAt) / 1_000_000, 0)
            LogTag.NETWORK.tree().d("%s %s failed after %d ms", request.method, request.url.encodedPath, (System.nanoTime() - startedAt) / 1_000_000)
            throw e
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(headersInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val httpClient: HttpClient = HttpClient(OkHttp) {
        engine { preconfigured = okHttpClient }
        install(ContentNegotiation) { json(json) }
        install(HttpCookies) { storage = cookies }
        defaultRequest { url(baseUrl) }
    }

    /** Exchanges the refresh cookie for a new access token from inside the interceptor chain. */
    private fun refreshBlocking(chain: Interceptor.Chain): String? {
        val refreshToken = session.refreshToken
        if (refreshToken == null) {
            LogTag.AUTH.tree().d("no refresh token")
            return null
        }
        val request = Request.Builder()
            .url("$baseUrl/api/v1/auth/refresh")
            .header("Cookie", "refresh_token=$refreshToken")
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        chain.proceed(request).use { response ->
            if (!response.isSuccessful) {
                LogTag.AUTH.tree().w("refresh failed: %d", response.code)
                return null
            }
            response.headers("Set-Cookie")
                .mapNotNull { okhttp3.Cookie.parse(request.url, it) }
                .lastOrNull { it.name == "refresh_token" && it.value.isNotEmpty() }
                ?.let { cookies.updateRefreshToken(it.value) }
            val body = response.body?.string()
            if (body == null || !body.trim().startsWith("{")) {
                LogTag.AUTH.tree().w("refresh returned no JSON")
                return null
            }
            val token = runCatching { (json.parseToJsonElement(body) as? JsonObject)?.get("accessToken") as? JsonPrimitive }
                .getOrNull()?.content?.takeIf { it.isNotEmpty() } ?: return null
            session.accessToken = token
            LogTag.AUTH.tree().d("access token refreshed")
            return token
        }
    }

    suspend inline fun <reified T> get(path: String, query: Map<String, Any?> = emptyMap()): T =
        request(HttpMethod.Get, path, null, query, serializer())

    suspend inline fun <reified T, reified B> post(path: String, body: B): T =
        request(HttpMethod.Post, path, json.encodeToJsonElement(serializer<B>(), body), emptyMap(), serializer())

    suspend inline fun <reified T, reified B> put(path: String, body: B): T =
        request(HttpMethod.Put, path, json.encodeToJsonElement(serializer<B>(), body), emptyMap(), serializer())

    suspend inline fun <reified T> post(path: String): T =
        request(HttpMethod.Post, path, null, emptyMap(), serializer())

    suspend fun delete(path: String) {
        send(HttpMethod.Delete, path)
    }

    suspend fun postEmpty(path: String) {
        send(HttpMethod.Post, path)
    }

    suspend fun <T> request(
        method: HttpMethod,
        path: String,
        body: JsonElement?,
        query: Map<String, Any?>,
        serializer: KSerializer<T>,
    ): T = json.decodeFromString(serializer, send(method, path, body, query).bodyAsText())

    suspend fun send(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        query: Map<String, Any?> = emptyMap(),
        onProgress: ((Long, Long?) -> Unit)? = null,
    ): HttpResponse {
        val response = exchange(method, path, body, query, onProgress = onProgress)
        if (!response.status.isSuccess()) throw apiError(response)
        return response
    }

    /** Executes a request without converting error statuses into [ApiException]. */
    suspend fun exchange(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        query: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        onProgress: ((Long, Long?) -> Unit)? = null,
    ): HttpResponse = httpClient.request(path) {
        this.method = method
        headers.forEach { (name, value) -> header(name, value) }
        url { query.forEach { (key, value) -> if (value != null) parameters.append(key, value.toString()) } }
        if (onProgress != null) onUpload { sent, total -> onProgress(sent, total) }
        when {
            body is JsonElement -> setBody(TextContent(body.toString(), ContentType.Application.Json))
            body != null -> setBody(body)
            method == HttpMethod.Post -> setBody(TextContent("", ContentType.Application.Json))
        }
    }

    /** Restores a session at startup; a rejected refresh token ends the session. */
    suspend fun refreshSession(): Boolean {
        if (session.refreshToken == null) return false
        val response = exchange(HttpMethod.Post, "/api/v1/auth/refresh")
        if (!response.status.isSuccess()) {
            if (response.status.value == 401 || response.status.value == 403) {
                session.clear()
                onSessionExpired?.invoke()
            }
            return false
        }
        session.accessToken = json.decodeFromString<LoginResponse>(response.bodyAsText()).accessToken
        return true
    }

    suspend fun apiError(response: HttpResponse): ApiException {
        val text = runCatching { response.bodyAsText() }.getOrDefault("<не удалось прочитать тело ответа>")
        val error = runCatching { json.decodeFromString<ApiErrorResponse>(text).error }.getOrNull()
        return ApiException(
            error?.code ?: "UNKNOWN",
            error?.message ?: text.take(500),
            response.status.value,
            error?.retryAfter,
            error?.attemptsLeft,
        )
    }

    fun close() = httpClient.close()
}

/** Keeps the refresh token cookie (scoped to /api/v1/auth) in persistent session storage. */
class RefreshTokenCookieStorage(private val session: SessionStorage, private val domain: String) : CookiesStorage {
    private val cookies = ArrayList<Cookie>()

    init {
        session.refreshToken?.let { cookies.add(refreshCookie(it)) }
    }

    private fun refreshCookie(value: String) =
        Cookie("refresh_token", value, domain = domain, path = "/api/v1/auth", secure = true)

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        synchronized(cookies) {
            cookies.removeAll { it.name == cookie.name }
            cookies.add(cookie)
        }
        if (cookie.name == "refresh_token" && cookie.value.isNotEmpty()) session.refreshToken = cookie.value
    }

    fun updateRefreshToken(value: String) {
        if (value.isEmpty()) return
        synchronized(cookies) {
            cookies.removeAll { it.name == "refresh_token" }
            cookies.add(refreshCookie(value))
        }
        session.refreshToken = value
    }

    fun clear() {
        synchronized(cookies) { cookies.clear() }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> = synchronized(cookies) {
        cookies.filter { cookie ->
            val cookieDomain = cookie.domain ?: return@filter false
            val host = requestUrl.host
            val domainMatches = host == cookieDomain || host.endsWith(".$cookieDomain") || host.endsWith(cookieDomain.trimStart('.'))
            domainMatches && requestUrl.encodedPath.startsWith(cookie.path ?: "/")
        }
    }

    override fun close() {
        synchronized(cookies) { cookies.clear() }
    }
}
