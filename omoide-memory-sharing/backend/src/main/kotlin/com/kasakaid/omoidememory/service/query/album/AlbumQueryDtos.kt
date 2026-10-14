package com.kasakaid.omoidememory.service.query.album

import com.kasakaid.omoidememory.domain.model.AlbumContent
import com.kasakaid.omoidememory.service.query.shared.ContentType
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

/** album_content.id（contentId）を含むアルバム詳細写真DTO。差し替えAPIの指定に使用する。 */
class AlbumDetailPhotoDto(
    val contentId: UUID,
    val id: UUID?,
    val type: String?,
    val commentedAt: OffsetDateTime,
    val captureTime: OffsetDateTime?,
    val commentCount: Int,
)

class AlbumDetailDto(
    val albumId: UUID,
    val albumName: String,
    val count: Int,
    val createdAt: OffsetDateTime,
    val periodFrom: LocalDate,
    val periodTo: LocalDate,
    val photos: List<AlbumDetailPhotoDto>,
)

class AlbumContentDto(
    val albumContentId: UUID,
    val photoId: UUID?,
    val type: String?,
    val captureTime: OffsetDateTime?,
    val isRandom: Boolean,
) {
    constructor(content: AlbumContent, isRandom: Boolean) : this(
        albumContentId = content.id,
        photoId = content.photoId,
        type = ContentType.PHOTO.value,
        captureTime = content.capturedAt,
        isRandom = isRandom,
    )
}

class AlbumCurrentStateDto(
    val albumId: UUID,
    val photos: List<AlbumContentDto>,
)
