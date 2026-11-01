package com.itd.app.data.analytics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DwellEventWire(
    @SerialName("md") val maxDwellMs: Int,
    @SerialName("et") val enterTsMs: Long,
    @SerialName("xt") val exitTsMs: Long,
    @SerialName("r") val reasonCode: Int,
    @SerialName("v") val vs: String,
    @SerialName("sc") val sourceContext: String? = null,
    @SerialName("s") val sourceCode: Int? = null,
    @SerialName("b") val subImpression: Int? = null,
)

@Serializable
data class DwellBatchWire(
    @SerialName("sid") val sessionId: String,
    @SerialName("e") val events: List<DwellEventWire>,
)

@Serializable
data class InteractionEventWire(
    @SerialName("t") val typeCode: Int,
    @SerialName("v") val vs: String,
    @SerialName("ai") val attachmentId: String,
    @SerialName("mi") val mediaIdx: Int? = null,
    @SerialName("pm") val playedMs: Long? = null,
    @SerialName("dm") val durationMs: Long? = null,
    @SerialName("s") val sourceCode: Int? = null,
)

@Serializable
data class InteractionBatchWire(
    @SerialName("sid") val sessionId: String,
    @SerialName("e") val events: List<InteractionEventWire>,
)
