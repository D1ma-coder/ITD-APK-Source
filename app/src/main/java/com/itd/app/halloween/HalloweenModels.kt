package com.itd.app.halloween

enum class HalloweenStage { CALM, ANGRY, RAGE, TAKEN }

enum class HalloweenMist { CLOUD, FOG, SPARKS, SWIRL, WISPS }

enum class HalloweenEdge { TOP, BOTTOM, LEFT, RIGHT, INSIDE }

enum class HalloweenResolution { EXORCISED, FREED }

data class HalloweenHolder(
    val userId: String,
    val username: String?,
    val displayName: String?,
    val avatar: String?,
)

data class HalloweenSaver(
    val userId: String,
    val username: String?,
)

data class HalloweenPossession(
    val id: String,
    val spawnedAt: Long,
    val takenAt: Long,
    val stage: HalloweenStage,
    val threshold: Int,
    val mist: HalloweenMist,
    val edge: HalloweenEdge,
    val holders: List<HalloweenHolder>,
    val heldByMe: Boolean,
    val weakness: Int,
    val resolved: HalloweenResolution?,
    val resolvedAt: Long?,
    val stampUntil: Long?,
    val savers: List<HalloweenSaver>?,
    val canThank: Boolean,
)

data class HalloweenWerewolf(
    val night: Boolean,
    val revealed: Boolean,
    val text: String?,
    val images: List<String>,
)

data class HalloweenPostHaunt(
    val possession: HalloweenPossession?,
    val werewolf: HalloweenWerewolf?,
)

data class HalloweenCandyDrop(
    val kind: String,
    val name: String,
    val isNew: Boolean,
    val count: Int,
)

class HalloweenFeedState(
    val serverNow: Long,
    val night: Boolean,
    val posts: HashMap<String, HalloweenPostHaunt>,
)

class HalloweenHoldResult(
    val holders: ArrayList<HalloweenHolder>,
    val threshold: Int,
    val resolved: HalloweenResolution?,
    val candy: HalloweenCandyDrop?,
)

class HalloweenMe(
    val sounds: Boolean,
    val soundsAsked: Boolean,
    val eventDay: Int?,
)

class HalloweenNotice(
    val id: String,
    val kind: String,
    val createdAt: String,
    val readAt: String?,
    val payload: Map<String, kotlinx.serialization.json.JsonElement>,
)

class HalloweenApiException(val status: Int, override val message: String?) : Exception(message)
