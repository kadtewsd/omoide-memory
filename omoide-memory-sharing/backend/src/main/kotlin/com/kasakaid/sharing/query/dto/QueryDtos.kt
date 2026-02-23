package com.kasakaid.sharing.query.dto

import java.time.OffsetDateTime

sealed interface MemoryFeedDto {
    val id: Long
    val type: String
    val filePath: String
    val captureTime: OffsetDateTime?

    class Photo(
        override val id: Long,
        override val type: String = "PHOTO",
        override val filePath: String,
        override val captureTime: OffsetDateTime?,
    ) : MemoryFeedDto

    class Video(
        override val id: Long,
        override val type: String = "VIDEO",
        override val filePath: String,
        override val captureTime: OffsetDateTime?,
        val thumbnailBase64: String?,
        val thumbnailMimeType: String?,
    ) : MemoryFeedDto
}

class CommentDto(
    val id: Long,
    val commenterName: String,
    val commenterIconBase64: String?,
    val commentBody: String,
    val commentedAt: OffsetDateTime,
)
