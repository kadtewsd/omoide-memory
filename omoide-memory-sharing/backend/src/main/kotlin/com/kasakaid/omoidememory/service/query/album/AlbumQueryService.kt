package com.kasakaid.omoidememory.service.query.album

import com.kasakaid.omoidememory.domain.model.AlbumStatus
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoideVideo
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM_CONTENT
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import com.kasakaid.omoidememory.service.query.shared.MemoryContentsQueryService
import com.kasakaid.omoidememory.service.query.shared.memoryfeed.ContentType
import com.kasakaid.omoidememory.service.query.shared.memoryfeed.FilterMode
import com.kasakaid.omoidememory.service.query.shared.memoryfeed.MemoryFeedDto
import com.kasakaid.omoidememory.service.query.shared.memoryfeed.MemoryFeedDtoConverter
import com.kasakaid.omoidememory.service.query.shared.memoryfeed.OmoideCondition
import com.kasakaid.omoidememory.service.query.shared.memoryfeed.OmoideMemoryFeedQueryService
import com.kasakaid.omoidememory.shared.adapter.NotFoundException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class AlbumQueryService(
    private val dslContext: DSLGenerator,
    private val memoryContentsQueryService: MemoryContentsQueryService,
    private val memmoryFeedQueryService: OmoideMemoryFeedQueryService,
) {
    suspend fun getAlbums(): List<AlbumSummaryDto> {
        val albumRecords =
            dslContext
                .invoke()
                .selectFrom(ALBUM)
                .where(ALBUM.STATUS.eq(AlbumStatus.CONFIRMED.name))
                .asFlow()
                .toList()

        if (albumRecords.isEmpty()) return emptyList()

        val contentRecords =
            dslContext
                .invoke()
                .selectFrom(ALBUM_CONTENT)
                .asFlow()
                .toList()

        val contentsByAlbumId = contentRecords.groupBy { it.albumId }

        return albumRecords
            .map { albumRecord ->
                val contents = contentsByAlbumId[albumRecord.id].orEmpty()
                AlbumSummaryDto(
                    albumId = albumRecord.id,
                    albumName = albumRecord.name,
                    count = contents.size,
                    createdAt = albumRecord.createdAt!!,
                    coverPhotoId = contents.firstOrNull()?.photoId,
                )
            }.sortedByDescending { it.createdAt }
    }

    suspend fun getAlbumDetail(albumId: UUID): AlbumDetailDto {
        val albumRecord =
            dslContext
                .invoke()
                .selectFrom(ALBUM)
                .where(ALBUM.ID.eq(albumId))
                .asFlow()
                .toList()
                .firstOrNull() ?: throw NotFoundException("Album not found with id: $albumId")

        val photoIds =
            dslContext
                .invoke()
                .select(ALBUM_CONTENT.PHOTO_ID)
                .from(ALBUM_CONTENT)
                .where(ALBUM_CONTENT.ALBUM_ID.eq(albumId))
                .orderBy(ALBUM_CONTENT.CAPTURED_AT.asc().nullsLast(), ALBUM_CONTENT.ID.asc())
                .asFlow()
                .toList()
                .map { it.value1()!! }

        val photos =
            if (photoIds.isNotEmpty()) {
                memoryContentsQueryService.fetchPhoto(SYNCED_OMOIDE_PHOTO.ID.`in`(photoIds))
            } else {
                emptyList()
            }

        val feedDtos = MemoryFeedDtoConverter.convert(Triple(photos, emptyList<SyncedOmoideVideo>(), emptyList<CommentOmoide>()))

        return AlbumDetailDto(
            albumId = albumId,
            albumName = albumRecord.name,
            count = photoIds.size,
            createdAt = albumRecord.createdAt!!,
            periodFrom = albumRecord.periodFrom!!,
            periodTo = albumRecord.periodTo!!,
            photos = feedDtos,
        )
    }

    suspend fun getRandomPhotosForAlbum(
        albumId: UUID,
        count: Int,
    ): List<MemoryFeedDto> {
        val albumRecord =
            dslContext
                .invoke()
                .selectFrom(ALBUM)
                .where(ALBUM.ID.eq(albumId))
                .asFlow()
                .toList()
                .firstOrNull() ?: throw NotFoundException("Album not found with id: $albumId")

        val excludedPhotoIds =
            dslContext
                .invoke()
                .select(ALBUM_CONTENT.PHOTO_ID)
                .from(ALBUM_CONTENT)
                .where(ALBUM_CONTENT.ALBUM_ID.eq(albumId))
                .asFlow()
                .toList()
                .mapNotNull { it.value1() }
                .toSet()

        val jstOffset = ZoneOffset.ofHours(9)
        val startInclusive = albumRecord.periodFrom!!.atStartOfDay().atOffset(jstOffset)
        val endExclusive =
            albumRecord.periodTo!!
                .plusDays(1)
                .atStartOfDay()
                .atOffset(jstOffset)

        val feedResponse =
            memmoryFeedQueryService.fetchFeedPage(
                condition =
                    OmoideCondition(
                        startInclusive = startInclusive,
                        endExclusive = endExclusive,
                        cursor = null,
                        filterMode = FilterMode.ALL,
                        contentType = ContentType.PHOTO,
                    ),
                limit = Int.MAX_VALUE,
            )

        return feedResponse.items
            .filterNot { item -> item.id != null && excludedPhotoIds.contains(item.id) }
            .shuffled()
            .take(count)
    }

    suspend fun getAlbumCurrentState(
        albumId: UUID,
        randomlyAddedPhotoIds: Set<UUID>,
    ): AlbumCurrentStateDto {
        val photoIds =
            dslContext
                .invoke()
                .select(ALBUM_CONTENT.PHOTO_ID)
                .from(ALBUM_CONTENT)
                .where(ALBUM_CONTENT.ALBUM_ID.eq(albumId))
                .orderBy(ALBUM_CONTENT.CAPTURED_AT.asc().nullsLast(), ALBUM_CONTENT.ID.asc())
                .asFlow()
                .toList()
                .map { it.value1()!! }

        val photos =
            if (photoIds.isNotEmpty()) {
                memoryContentsQueryService.fetchPhoto(SYNCED_OMOIDE_PHOTO.ID.`in`(photoIds))
            } else {
                emptyList()
            }

        val feedDtos =
            MemoryFeedDtoConverter.convert(
                Triple(photos, emptyList<SyncedOmoideVideo>(), emptyList<CommentOmoide>()),
            )

        val photoIdOrder = photoIds.withIndex().associate { (index, id) -> id to index }
        val albumContents =
            feedDtos
                .sortedWith(compareBy(nullsLast()) { photoIdOrder[it.id] })
                .map { dto ->
                    AlbumContentDto(
                        id = dto.id,
                        type = dto.type,
                        commentedAt = dto.commentedAt,
                        captureTime = dto.captureTime,
                        commentCount = dto.commentCount,
                        isRandom = dto.id != null && randomlyAddedPhotoIds.contains(dto.id),
                    )
                }

        return AlbumCurrentStateDto(albumId = albumId, photos = albumContents)
    }
}
