package com.itd.app.ui.navigation

import java.net.URI
import java.net.URLDecoder

sealed interface AppLink {
    data class Profile(val username: String) : AppLink
    data class Post(val id: String, val commentId: String? = null) : AppLink
    data class Qr(val url: String) : AppLink
    data object Sessions : AppLink
    data object Shop : AppLink
    data class Event(val link: String?) : AppLink
}

object AppLinkParser {
    fun parse(value: String): AppLink? = runCatching {
        val uri = URI(value)
        val path = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty).map(::decode)
        val comment = uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull {
            val pair = it.split('=', limit = 2)
            if (decode(pair[0]) == "comment") pair.getOrNull(1)?.let(::decode) else null
        }?.takeIf(String::isNotBlank)
        when (uri.scheme?.lowercase()) {
            "itd" -> when (uri.host) {
                "profile" -> path.firstOrNull()?.let(AppLink::Profile)
                "post" -> path.firstOrNull()?.let { AppLink.Post(it, comment) }
                "sessions" -> AppLink.Sessions
                "event" -> AppLink.Event(uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull {
                    val pair = it.split('=', limit = 2)
                    if (decode(pair[0]) == "link") pair.getOrNull(1)?.let(::decode) else null
                })
                else -> null
            }
            "https" -> {
                val host = uri.host ?: uri.rawAuthority
                if (host?.lowercase() !in setOf("xn--d1ah4a.com", "итд.com")) return null
                val username = path.firstOrNull()?.takeIf { it.startsWith('@') }?.drop(1)?.takeIf(String::isNotBlank)
                when {
                    path == listOf("qr") -> AppLink.Qr(value)
                    path.firstOrNull() == "shop" -> AppLink.Shop
                    username != null && path.size >= 3 && path[1] == "post" -> AppLink.Post(path[2], comment)
                    username != null -> AppLink.Profile(username)
                    else -> null
                }
            }
            else -> null
        }
    }.getOrNull()

    private fun decode(value: String) = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
}
