package com.itd.app.halloween

import android.content.Context
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest

object HalloweenArt {
    val names = listOf(
        "ghost", "ghost-orig", "ghost-hat", "ghost-eyes", "g-body", "g-arm-l", "g-arm-r", "g-face-smirk", "g-face-blink",
        "g-face-strain", "g-face-weak", "g-face-surprised", "puff", "fire", "hand-grab", "hand-point", "candle", "lantern",
        "bat", "bat-body", "bat-wing-l", "bat-wing-r", "moon", "full-moon", "ear-l", "ear-r", "web-corner", "web-corner-br",
        "jar", "jar-front", "wrapper-closed", "wrapper-open", "pumpkin-body", "toffee", "chocolate-bat", "eye-lollipop",
        "wolf-fang", "sour-ghost", "moon-lollipop",
    )

    fun url(name: String) = "https://xn--d1ah4a.com/public/events/halloween/art/$name.webp"

    fun prefetch(context: Context) {
        val loader = Coil.imageLoader(context)
        names.forEach { name ->
            loader.enqueue(
                ImageRequest.Builder(context).data(url(name))
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
            )
        }
    }
}

/** Event preferences ("halloween_flags"): dismissed prompts and once-a-day notices. */
object HalloweenFlags {
    var preferences: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (preferences == null) preferences = context.applicationContext.getSharedPreferences("halloween_flags", Context.MODE_PRIVATE)
    }

    fun get(key: String) = preferences?.getBoolean(key, false) == true

    fun set(key: String) {
        preferences?.edit()?.putBoolean(key, true)?.apply()
    }
}

/** Callbacks the event layer uses to navigate; set by the UI that hosts the event. */
object HalloweenNavigation {
    var openEventSection: (String?) -> Unit = {}
    var openPost: (username: String, postId: String) -> Unit = { _, _ -> }
    var openProfile: (String) -> Unit = {}
    var sharePost: (username: String, postId: String, text: String) -> Unit = { _, _, _ -> }
}
