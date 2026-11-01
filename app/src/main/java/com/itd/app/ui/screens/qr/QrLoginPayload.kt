package com.itd.app.ui.screens.qr

import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.UUID

data class QrLoginPayload(val qrId: String, val secret: String)

object QrLoginParser {
    fun parse(value: String): QrLoginPayload? = runCatching {
        val uri = URI(value.trim())
        if (!uri.scheme.equals("https", true) || uri.host?.lowercase(Locale.ROOT) != "xn--d1ah4a.com" || uri.path != "/qr") return null
        val fragment = uri.rawFragment?.takeIf { it.isNotEmpty() } ?: return null
        val fields = fragment.split('&').mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size == 2) pieces[0] to URLDecoder.decode(pieces[1].replace("+", "%2B"), "UTF-8") else null
        }.toMap()
        val id = fields["i"] ?: return null
        val secret = fields["s"] ?: return null
        if (!UUID.fromString(id).toString().equals(id, true) || !secret.matches(Regex("[0-9a-f]{64}"))) return null
        QrLoginPayload(id, secret)
    }.getOrNull()
}
