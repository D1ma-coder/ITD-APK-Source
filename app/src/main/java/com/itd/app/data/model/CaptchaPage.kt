package com.itd.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CaptchaPage(
    val provider: String,
    val field: String,
    val url: String,
) {
    companion object {
        val FALLBACK = CaptchaPage(
            provider = "cloudflare",
            field = "turnstileToken",
            url = "https://captcha.xn--d1ah4a.com/cf.html",
        )
    }
}
