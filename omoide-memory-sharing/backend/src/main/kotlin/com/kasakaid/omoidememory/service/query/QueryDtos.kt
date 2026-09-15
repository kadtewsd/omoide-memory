package com.kasakaid.omoidememory.service.query

import java.time.OffsetDateTime
import java.util.UUID

class MemoryFeedDto(
    val id: UUID?,
    val type: String?,
    val commentedAt: OffsetDateTime,
    val captureTime: OffsetDateTime?,
    val thumbnailBase64: String?,
    val thumbnailMimeType: String?,
    val commentCount: Int,
)


class CommentDto(
    val id: UUID,
    val commenterName: String,
    val commenterIconBase64: String?,
    val commentBody: String,
    val commentedAt: OffsetDateTime,
)
