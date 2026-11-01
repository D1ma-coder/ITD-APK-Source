package com.itd.app.halloween

import com.itd.app.halloween.HalloweenJson.array
import com.itd.app.halloween.HalloweenJson.bool
import com.itd.app.halloween.HalloweenJson.field
import com.itd.app.halloween.HalloweenJson.int
import com.itd.app.halloween.HalloweenJson.obj
import com.itd.app.halloween.HalloweenJson.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

class HalloweenToastContent(val title: String, val sub: String?, val art: String?, val actions: List<HalloweenToastAction>)

object HalloweenNoticeTexts {
    private val repeatPhrases = listOf("Повторка", "Дежавю", "Снова здравствуйте", "Дубль, можно дарить", "Ну привет ещё раз", "Копия-копия")

    val candyNames = mapOf(
        "toffee" to "Ириску",
        "chocolate-bat" to "Шоколадную мышь",
        "eye-lollipop" to "Леденец-глаз",
        "wolf-fang" to "Волчий клык",
        "sour-ghost" to "Кислого призрака",
        "moon-lollipop" to "Лунный леденец",
    )

    fun candyText(candy: HalloweenCandyDrop): String {
        if (candy.isNew || candy.count <= 1) return "Конфета: ${candy.name}"
        return "${candy.name}. ${repeatPhrases[candy.count % repeatPhrases.size]}"
    }

    fun holdersNeeded(holders: Int, threshold: Int): String = when (val left = threshold - holders) {
        in Int.MIN_VALUE..1 -> "Нужен ещё один"
        2 -> "Нужно ещё двое"
        3 -> "Нужно ещё трое"
        else -> "Нужно ещё $left"
    }

    fun saversTitle(savers: List<HalloweenSaver>): String {
        val first = savers.firstOrNull() ?: return "Твой пост спасли от призрака"
        val name = first.username?.let { "@$it" } ?: "кто-то"
        return if (savers.size == 1) "$name спас(ла) твой пост от призрака" else "$name и ещё ${savers.size - 1} спасли твой пост"
    }

    fun plural(count: Int, one: String, few: String, many: String): String {
        val mod10 = count % 10
        val mod100 = count % 100
        if (mod10 == 1 && mod100 != 11) return "$count $one"
        if (mod10 !in 2..4 || mod100 in 12..14) return "$count $many"
        return "$count $few"
    }

    private fun count(key: String, payload: Map<String, JsonElement>) = payload[key].int() ?: 0

    private fun fromUsername(payload: Map<String, JsonElement>) = payload["from"].field("username").string()

    /** Builds the toast for a server notice, or null when it has nothing to show. */
    fun toast(notice: HalloweenNotice, username: String?): HalloweenToastContent? {
        val payload = notice.payload
        when (notice.kind) {
            "thanks" -> {
                val from = fromUsername(payload)
                val postId = payload["postId"].string() ?: ""
                val actions = if (from == null || postId.isEmpty()) emptyList()
                else listOf(HalloweenToastAction("Открыть пост") { HalloweenNavigation.openPost(from, postId) })
                return HalloweenToastContent("Тебе сказали спасибо", (from?.let { "@$it" } ?: "кто-то") + ", за спасённый пост", "lantern", actions)
            }
            "lantern" -> {
                val level = payload["level"].int() ?: 0
                return HalloweenToastContent(
                    "Тебе сказали спасибо",
                    "Фонарь горит ярче: уровень $level",
                    "lantern",
                    listOf(HalloweenToastAction("Мой фонарь") { HalloweenNavigation.openEventSection("pumpkin") }),
                )
            }
            "gift" -> {
                val kind = payload["kind"].string() ?: return null
                val name = payload["name"].string() ?: return null
                val from = fromUsername(payload)?.let { "@$it" } ?: "кто-то"
                return HalloweenToastContent(
                    "$from подарил(а) тебе ${candyNames[kind] ?: name}",
                    "Уже в банке",
                    kind,
                    listOf(HalloweenToastAction("Открыть банку") { HalloweenNavigation.openEventSection("jar") }),
                )
            }
            "saved" -> {
                val possessionId = payload["possessionId"].string() ?: ""
                val savers = payload["savers"].array().orEmpty().mapNotNull { element ->
                    val saver = element.obj() ?: return@mapNotNull null
                    val userId = saver["userId"].string() ?: return@mapNotNull null
                    val name = saver["username"] ?: return@mapNotNull null
                    if (name.string() == null && name !is JsonNull) return@mapNotNull null
                    HalloweenSaver(userId, name.string())
                }
                val thanked = payload["thanked"].bool() == true
                val postId = payload["postId"].string() ?: ""
                val actions = buildList {
                    if (possessionId.isNotEmpty() && !thanked) add(HalloweenToastAction("Сказать спасибо") { HalloweenNotices.thank(possessionId) })
                    if (username != null && postId.isNotEmpty()) add(HalloweenToastAction("Открыть пост") { HalloweenNavigation.openPost(username, postId) })
                }
                val sub = if (thanked) "Спасибо уже отправлено, фонарь спасителя стал ярче" else "Скажи спасибо, фонарь спасителя станет ярче"
                return HalloweenToastContent(saversTitle(savers), sub, "lantern", actions)
            }
            "taken" -> {
                val postId = payload["postId"].string() ?: ""
                val actions = if (username == null || postId.isEmpty()) emptyList() else listOf(
                    HalloweenToastAction("Открыть пост") { HalloweenNavigation.openPost(username, postId) },
                    HalloweenToastAction("Позвать друзей") {
                        HalloweenNavigation.sharePost(username, postId, "Мой пост захватил призрак, помоги освободить его фонарём")
                    },
                )
                return HalloweenToastContent(
                    "Призрак захватил твой пост",
                    "Зови друзей: пусть жмут «Освободить» и ловят призрака фонарём",
                    "ghost-orig",
                    actions,
                )
            }
            "morning" -> {
                val parts = listOfNotNull(
                    count("candies", payload).takeIf { it > 0 }?.let { plural(it, "конфета", "конфеты", "конфет") },
                    count("thanks", payload).takeIf { it > 0 }?.let { "$it спасибо" },
                    count("gifts", payload).takeIf { it > 0 }?.let { plural(it, "подарок", "подарка", "подарков") },
                    count("werewolves", payload).takeIf { it > 0 }?.let { plural(it, "оборотень", "оборотня", "оборотней") },
                )
                if (parts.isEmpty()) return null
                return HalloweenToastContent(
                    "Что было за ночь",
                    parts.joinToString(", "),
                    "moon",
                    listOf(HalloweenToastAction("Что было ночью") { HalloweenNavigation.openEventSection("notices") }),
                )
            }
            "freed", "exorcised" -> {
                val candies = payload["candies"].array().orEmpty().mapNotNull { element ->
                    val candy = element.obj() ?: return@mapNotNull null
                    val kind = candy["kind"].string() ?: return@mapNotNull null
                    val name = candy["name"].string() ?: return@mapNotNull null
                    HalloweenCandyDrop(kind, name, candy["isNew"].bool() == true, candy["count"].int() ?: 0)
                }
                val anyNew = candies.any { it.isNew }
                val sub = when {
                    anyNew -> "Уже в банке"
                    candies.size > 1 -> "Повторы можно подарить другу"
                    candies.isNotEmpty() -> "Повтор можно подарить другу"
                    else -> "Конфета не выпала. Лови следующего, повезёт"
                }
                val actions = when {
                    anyNew -> listOf(HalloweenToastAction("Открыть банку") { HalloweenNavigation.openEventSection("jar") })
                    candies.isNotEmpty() -> listOf(HalloweenToastAction("Подарить другу") { HalloweenNavigation.openEventSection("jar") })
                    else -> emptyList()
                }
                val title = when {
                    candies.size >= 2 -> "Двойной улов: ${candies[0].name} и ${candies[1].name}"
                    candies.size == 1 -> candyText(candies[0])
                    else -> "Призрак упокоен"
                }
                return HalloweenToastContent(title, sub, candies.firstOrNull()?.kind ?: "puff", actions)
            }
        }
        val text = payload["text"].string()?.takeIf { it.isNotEmpty() } ?: return null
        return HalloweenToastContent(text, null, null, emptyList())
    }
}
