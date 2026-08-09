package com.kasakaid.omoidememory.service.query.album

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoideVideo
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.service.query.MemoryFeedDtoConverter
import com.kasakaid.omoidememory.service.query.shared.MemoryContentsQueryService
import com.kasakaid.omoidememory.shared.adapter.NotFoundException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

@Service
class AlbumQueryService(
    private val dslContext: DSLContext,
    private val memoryContentsQueryService: MemoryContentsQueryService,
    private val memoryFeedDtoConverter: MemoryFeedDtoConverter,
) {
    suspend fun getAlbums(): List<AlbumSummaryDto> {
        val albumPhotoRecords =
            dslContext
                .selectFrom(ALBUM_PHOTO)
                .asFlow()
                .toList()

        if (albumPhotoRecords.isEmpty()) return emptyList()

        val grouped = albumPhotoRecords.groupBy { it.albumId }
        val allPhotoIds = albumPhotoRecords.mapNotNull { it.photoId }.distinct()

        val photos =
            if (allPhotoIds.isNotEmpty()) {
                memoryContentsQueryService.fetchPhoto(SYNCED_OMOIDE_PHOTO.ID.`in`(allPhotoIds))
            } else {
                emptyList()
            }

        val photoMap = photos.associateBy { it.id }

        return grouped
            .map { (albumId, records) ->
                val firstRecord = records.first()
                val albumName = firstRecord.albumName ?: ""
                val createdAt = records.mapNotNull { it.createdAt }.minOrNull() ?: OffsetDateTime.now()
                val count = records.size

                val firstPhoto = records.mapNotNull { it.photoId }.firstNotNullOfOrNull { photoMap[it] }
                val coverPhotoBase64 =
                    firstPhoto?.serverPath?.let { serverPath ->
                        try {
                            val path = Paths.get(serverPath)
                            if (Files.exists(path)) {
                                Base64.getEncoder().encodeToString(Files.readAllBytes(path))
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                AlbumSummaryDto(
                    albumId = albumId,
                    albumName = albumName,
                    count = count,
                    createdAt = createdAt,
                    coverPhotoBase64 = coverPhotoBase64,
                )
            }.sortedByDescending { it.createdAt }
    }

    suspend fun getAlbumDetail(albumId: UUID): AlbumDetailDto {
        val albumPhotoRecords =
            dslContext
                .selectFrom(ALBUM_PHOTO)
                .where(ALBUM_PHOTO.ALBUM_ID.eq(albumId))
                .asFlow()
                .toList()

        if (albumPhotoRecords.isEmpty()) {
            throw NotFoundException("Album not found with id: $albumId")
        }

        val firstRecord = albumPhotoRecords.first()
        val albumName = firstRecord.albumName ?: ""
        val createdAt = albumPhotoRecords.mapNotNull { it.createdAt }.minOrNull() ?: OffsetDateTime.now()
        val photoIds = albumPhotoRecords.mapNotNull { it.photoId }

        val photos =
            if (photoIds.isNotEmpty()) {
                memoryContentsQueryService.fetchPhoto(SYNCED_OMOIDE_PHOTO.ID.`in`(photoIds))
            } else {
                emptyList()
            }

        val feedDtos = memoryFeedDtoConverter.convert(Triple(photos, emptyList(), emptyList()))

        return AlbumDetailDto(
            albumId = albumId,
            albumName = albumName,
            count = albumPhotoRecords.size,
            createdAt = createdAt,
            photos = feedDtos,
        )
    }
}
