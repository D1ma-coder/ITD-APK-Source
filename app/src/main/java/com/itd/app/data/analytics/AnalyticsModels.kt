package com.itd.app.data.analytics

/** Where a post impression happened. */
enum class ImpressionSource(val wireCode: Int, val apiName: String) {
    FEED_GLOBAL(1, "feed_global"),
    FEED_FOLLOWING(2, "feed_following"),
    FEED_CLAN(3, "feed_clan"),
    PROFILE(4, "profile"),
    HASHTAG(5, "hashtag"),
    POST_PAGE(6, "post_page"),
    LINK(7, "link"),
    SEARCH(8, "search");

    /** Only post pages and links send their source code; feeds are inferred server-side. */
    val sentCode: Int?
        get() = if (this == POST_PAGE || this == LINK) wireCode else null
}

enum class HiddenReason(val code: Int, val apiName: String) {
    NOT_IN_VIEWPORT(0, "NOT_IN_VIEWPORT"),
    BACKGROUNDED(1, "BACKGROUNDED"),
    PUSH_VIEW_HIDDEN(2, "PUSH_VIEW_HIDDEN"),
    PAGE_UNLOAD(3, "PAGE_UNLOAD"),
    COMPONENT_UNMOUNTED(4, "COMPONENT_UNMOUNTED"),
    USER_INACTIVE(5, "USER_INACTIVE"),
}

enum class InteractionType(val wireCode: Int, val apiName: String) {
    PHOTO_OPEN(1, "photo_open"),
    VIDEO_PROGRESS(2, "video_progress"),
}

data class DwellEvent(
    val postId: String,
    val maxDwellMs: Int,
    val sumDwellMs: Int,
    val enterTsMs: Long,
    val exitTsMs: Long,
    val hiddenReason: HiddenReason,
    val source: ImpressionSource,
    val sourceContext: String,
    val vs: String,
    val subImpression: Boolean,
)

sealed class InteractionEvent {
    abstract val postId: String?
    abstract val vs: String?
    abstract val source: ImpressionSource
    abstract val type: InteractionType

    data class PhotoOpen(
        override val postId: String?,
        override val vs: String?,
        val attachmentId: String?,
        override val source: ImpressionSource,
        val mediaIdx: Int,
    ) : InteractionEvent() {
        override val type = InteractionType.PHOTO_OPEN
    }

    data class VideoProgress(
        override val postId: String,
        override val vs: String,
        val attachmentId: String,
        override val source: ImpressionSource,
        val playedMs: Long,
        val durationMs: Long,
    ) : InteractionEvent() {
        override val type = InteractionType.VIDEO_PROGRESS
    }
}

internal class DwellTimer(val vs: String, val source: ImpressionSource, val sourceContext: String, val enteredAt: Long)

data class ActiveDwell(val postId: String, val source: ImpressionSource, val sourceContext: String, val enteredAt: Long)

data class DwellLogEntry(
    val postId: String,
    val source: ImpressionSource,
    val dwellMs: Int,
    val reason: HiddenReason,
    val subImpression: Boolean,
    val exitTsMs: Long,
)

data class InteractionLogEntry(
    val type: InteractionType,
    val postId: String,
    val source: ImpressionSource,
    val attachmentId: String,
    val mediaIdx: Int?,
    val playedMs: Long?,
    val durationMs: Long?,
    val loggedAt: Long,
)

data class BatchSnapshot(
    val endpoint: String,
    val eventCount: Int,
    val sentAt: Long,
    val durationMs: Long,
    val httpStatus: Int?,
    val error: String?,
)
