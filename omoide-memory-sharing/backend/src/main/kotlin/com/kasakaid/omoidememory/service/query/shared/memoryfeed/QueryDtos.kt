package com.kasakaid.omoidememory.service.query.shared.memoryfeed

import java.time.OffsetDateTime
import java.util.UUID

class MemoryFeedDto(
    val id: UUID?,
    val type: String?,
    val commentedAt: OffsetDateTime,
    val captureTime: OffsetDateTime?,
    val commentCount: Int,
)

class CommentDto(
    val id: UUID,
    val commenterName: String,
    val commenterIconBase64: String?,
    val commentBody: String,
    val commentedAt: OffsetDateTime,
)

class FeedCursor(
    val captureTime: OffsetDateTime,
    val id: UUID,
)

class FeedPageResponse(
    val items: List<MemoryFeedDto>,
    val nextCursor: FeedCursor?,
    val hasNext: Boolean,
)

enum class FilterMode {
    COMMENT_ONLY,
    ALL,
}

enum class ContentType {
    PHOTO,
    VIDEO,
    ALL,
}

class OmoideCondition(
    val startInclusive: OffsetDateTime?,
    val endExclusive: OffsetDateTime?,
    val cursor: FeedCursor?,
    val filterMode: FilterMode,
    val contentType: ContentType,
)
