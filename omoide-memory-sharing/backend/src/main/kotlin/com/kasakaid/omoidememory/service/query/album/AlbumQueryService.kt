package com.kasakaid.omoidememory.service.query.album

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.SyncedOmoideVideo
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM_CONTENT
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import com.kasakaid.omoidememory.service.query.shared.MemoryContentsQueryService
import com.kasakaid.omoidememory.service.query.shared.memoryfeed.MemoryFeedDtoConverter
import com.kasakaid.omoidememory.shared.adapter.NotFoundException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AlbumQueryService(
    private val dslContext: DSLGenerator,
    private val memoryContentsQueryService: MemoryContentsQueryService,
) {
    suspend fun getAlbums(): List<AlbumSummaryDto> {
        val albumRecords =
            dslContext
                .invoke()
                .selectFrom(ALBUM)
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
            photos = feedDtos,
        )
    }
}
