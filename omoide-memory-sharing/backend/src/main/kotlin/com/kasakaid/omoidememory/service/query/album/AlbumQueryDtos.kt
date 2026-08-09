package com.kasakaid.omoidememory.service.query.album

import com.kasakaid.omoidememory.service.query.MemoryFeedDto
import java.time.OffsetDateTime
import java.util.UUID

class AlbumSummaryDto(
    val albumId: UUID,
    val albumName: String,
    val count: Int,
    val createdAt: OffsetDateTime,
    val coverPhotoBase64: String?,
)

class AlbumDetailDto(
    val albumId: UUID,
    val albumName: String,
    val count: Int,
    val createdAt: OffsetDateTime,
    val photos: List<MemoryFeedDto>,
)
