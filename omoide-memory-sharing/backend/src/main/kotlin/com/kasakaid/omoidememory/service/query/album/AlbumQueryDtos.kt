package com.kasakaid.omoidememory.service.query.album

import com.kasakaid.omoidememory.service.query.shared.memoryfeed.MemoryFeedDto
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class AlbumSummaryDto(
    val albumId: UUID,
    val albumName: String,
    val count: Int,
    val createdAt: OffsetDateTime,
    val coverPhotoId: UUID?,
)

class AlbumDetailDto(
    val albumId: UUID,
    val albumName: String,
    val count: Int,
    val createdAt: OffsetDateTime,
    val periodFrom: LocalDate,
    val periodTo: LocalDate,
    val photos: List<MemoryFeedDto>,
)
