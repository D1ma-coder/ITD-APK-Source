package com.itd.app.halloween

import com.itd.app.data.remote.ApiClient
import com.itd.app.halloween.HalloweenJson.array
import com.itd.app.halloween.HalloweenJson.bool
import com.itd.app.halloween.HalloweenJson.double
import com.itd.app.halloween.HalloweenJson.epochMillis
import com.itd.app.halloween.HalloweenJson.field
import com.itd.app.halloween.HalloweenJson.int
import com.itd.app.halloween.HalloweenJson.obj
import com.itd.app.halloween.HalloweenJson.string
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HalloweenRepository(private val client: ApiClient) {

    suspend fun createWerewolf(postId: String, nightText: String, nightImages: ArrayList<String>) {
        val body = JsonObject(
            mapOf(
                "postId" to JsonPrimitive(postId),
                "nightText" to JsonPrimitive(nightText),
                "nightImages" to JsonArray(nightImages.map(::JsonPrimitive)),
            ),
        )
        post("/api/v1/halloween/werewolves", body.toString())
    }

    suspend fun feedState(postIds: List<String>): HalloweenFeedState? {
        val root = get("/api/v1/halloween/feed-state?posts=" + postIds.joinToString(",", transform = ::segment)).obj()
            ?: return null
        val posts = root["posts"].obj() ?: return null
        val serverNow = epochMillis(root["serverNow"].string()) ?: return null
        val haunts = HashMap<String, HalloweenPostHaunt>(posts.size)
        for ((postId, value) in posts) {
            val possession = parsePossession(value.field("possession").obj())
            val werewolf = parseWerewolf(value.field("werewolf").obj())
            if (possession != null || werewolf != null) haunts[postId] = HalloweenPostHaunt(possession, werewolf)
        }
        return HalloweenFeedState(serverNow, root["night"].bool() == true, haunts)
    }

    suspend fun hold(possessionId: String): HalloweenHoldResult? {
        val root = post("/api/v1/halloween/possessions/${segment(possessionId)}/hold", "{}").obj() ?: return null
        val holders = root["holders"].array() ?: return null
        return HalloweenHoldResult(
            holders = holders.mapNotNullTo(ArrayList(), ::parseHolder),
            threshold = root["threshold"].int() ?: 2,
            resolved = enumValue<HalloweenResolution>(root["resolved"].string()),
            candy = parseCandy(root["candy"].obj()),
        )
    }

    suspend fun jar(): List<Pair<String, Int>> {
        val kinds = get("/api/v1/halloween/jar").field("kinds").array() ?: emptyList()
        return kinds.mapNotNull { kind ->
            kind.field("id").string()?.let { it to (kind.field("count").int() ?: 0) }
        }
    }

    suspend fun me(): HalloweenMe {
        val root = get("/api/v1/halloween/me")
        val settings = root.field("settings")
        return HalloweenMe(
            sounds = settings.field("sounds").bool() == true,
            soundsAsked = settings.field("soundsAsked").bool() == true,
            eventDay = root.field("eventDay").int(),
        )
    }

    suspend fun notices(): List<HalloweenNotice> {
        val notices = get("/api/v1/halloween/notices").field("notices").array() ?: emptyList()
        return notices.mapNotNull { element ->
            val notice = element.obj() ?: return@mapNotNull null
            val id = notice["id"].string() ?: return@mapNotNull null
            val kind = notice["kind"].string() ?: return@mapNotNull null
            HalloweenNotice(
                id = id,
                kind = kind,
                createdAt = notice["createdAt"].string() ?: "",
                readAt = notice["readAt"].string(),
                payload = notice["payload"].obj() ?: emptyMap(),
            )
        }
    }

    suspend fun reactToWerewolf(postId: String): HalloweenCandyDrop? =
        parseCandy(post("/api/v1/halloween/werewolves/${segment(postId)}/react", "{}").field("candy").obj())

    suspend fun markNoticesRead(ids: ArrayList<String>) {
        post("/api/v1/halloween/notices/read", JsonObject(mapOf("ids" to JsonArray(ids.map(::JsonPrimitive)))).toString())
    }

    suspend fun updateSettings(sounds: Boolean) {
        val body = buildJsonObject {
            put("sounds", sounds)
            put("soundsAsked", true)
        }
        parse(client.exchange(HttpMethod.Put, "/api/v1/halloween/me/settings", json(body.toString()), headers = idempotency()))
    }

    suspend fun portal(): JsonElement? = get("/api/v1/portal")

    suspend fun thank(possessionId: String) {
        post("/api/v1/halloween/possessions/${segment(possessionId)}/thanks", "{}")
    }

    private suspend fun get(path: String): JsonElement? = parse(client.exchange(HttpMethod.Get, path))

    private suspend fun post(path: String, body: String?): JsonElement? =
        parse(client.exchange(HttpMethod.Post, path, json(body ?: "null"), headers = idempotency()))

    private suspend fun parse(response: HttpResponse): JsonElement? {
        val text = response.bodyAsText()
        return withContext(Dispatchers.Default) {
            val element = if (text.isBlank()) null else runCatching { HalloweenJson.json.parseToJsonElement(text) }.getOrNull()
            if (response.status.isSuccess()) return@withContext element
            val message = element.field("error").field("message").string() ?: element.field("message").string()
            throw HalloweenApiException(response.status.value, message?.takeIf { it.isNotBlank() })
        }
    }

    private fun parsePossession(possession: JsonObject?): HalloweenPossession? {
        if (possession == null) return null
        val id = possession["id"].string() ?: return null
        val stage = enumValue<HalloweenStage>(possession["stage"].string()) ?: return null
        val spawnedAt = epochMillis(possession["spawnedAt"].string()) ?: return null
        val takenAt = epochMillis(possession["takenAt"].string()) ?: return null
        val holders = possession["holders"].array() ?: return null
        val threshold = (possession["threshold"].double()?.toInt() ?: 0).takeIf { it > 0 } ?: 2
        return HalloweenPossession(
            id = id,
            spawnedAt = spawnedAt,
            takenAt = takenAt,
            stage = stage,
            threshold = threshold,
            mist = enumValue<HalloweenMist>(possession["mist"].string()) ?: HalloweenMist.CLOUD,
            edge = enumValue<HalloweenEdge>(possession["edge"].string()) ?: HalloweenEdge.TOP,
            holders = holders.mapNotNull(::parseHolder),
            heldByMe = possession["heldByMe"].bool() == true,
            weakness = (possession["weakness"].double() ?: 0.0).toInt().coerceIn(0, 100),
            resolved = enumValue<HalloweenResolution>(possession["resolved"].string()),
            resolvedAt = epochMillis(possession["resolvedAt"].string()),
            stampUntil = epochMillis(possession["stampUntil"].string()),
            savers = possession["savers"].array()?.mapNotNull(::parseSaver),
            canThank = possession["canThank"].bool() == true,
        )
    }

    private fun parseWerewolf(werewolf: JsonObject?): HalloweenWerewolf? {
        if (werewolf == null) return null
        val night = werewolf["night"].bool() ?: return null
        val revealed = werewolf["revealed"].bool() == true
        val text = werewolf["text"].string()
        if ((night || revealed) && text != null) {
            val images = werewolf["images"].array()?.mapNotNull { it.string() } ?: emptyList()
            return HalloweenWerewolf(night, revealed, text, images)
        }
        return HalloweenWerewolf(false, revealed, null, emptyList())
    }

    private fun parseHolder(element: JsonElement): HalloweenHolder? {
        val holder = element.obj() ?: return null
        val userId = holder["userId"].string() ?: return null
        val username = holder["username"] ?: return null
        if (username.string() == null && username !is JsonNull) return null
        return HalloweenHolder(userId, username.string(), holder["displayName"].string(), holder["avatar"].string())
    }

    private fun parseSaver(element: JsonElement): HalloweenSaver? {
        val saver = element.obj() ?: return null
        val userId = saver["userId"].string() ?: return null
        val username = saver["username"] ?: return null
        if (username.string() == null && username !is JsonNull) return null
        return HalloweenSaver(userId, username.string())
    }

    private fun parseCandy(candy: JsonObject?): HalloweenCandyDrop? {
        if (candy == null) return null
        val kind = candy["kind"].string() ?: return null
        val name = candy["name"].string() ?: return null
        return HalloweenCandyDrop(kind, name, candy["isNew"].bool() == true, candy["count"].int() ?: 0)
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?): T? =
        value?.let { name -> enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) } }

    private fun json(body: String) = TextContent(body, ContentType.Application.Json)

    private fun idempotency() = mapOf("Idempotency-Key" to UUID.randomUUID().toString())

    companion object {
        fun segment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
