package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.domain.model.AlbumContent
import com.kasakaid.omoidememory.domain.model.AlbumId
import com.kasakaid.omoidememory.domain.repository.AlbumContentsRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM_CONTENT
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.time.OffsetDateTime
import java.util.UUID

@Component
class JooqAlbumContentsRepository(
    private val dslContext: DSLGenerator,
) : AlbumContentsRepository {
    override suspend fun fetchByAlbumId(albumId: AlbumId): List<AlbumContent> =
        ALBUM_CONTENT.run {
            return dslContext
                .invoke()
                .selectFrom(ALBUM_CONTENT)
                .where(ALBUM_ID.eq(albumId))
                .orderBy(CAPTURED_AT.asc().nullsLast(), ALBUM_CONTENT.ID.asc())
                .asFlow()
                .toList()
                .map {
                    AlbumContent(albumId = it.albumId, photoId = it.photoId, capturedAt = it.capturedAt)
                }
        }

    override suspend fun add(content: AlbumContent): AlbumContent {
        addContent(listOf(content))
        return content
    }

    override suspend fun add(albumContents: List<AlbumContent>): List<AlbumContent> {
        addContent(albumContents)
        return albumContents
    }

    private suspend fun addContent(albumContent: List<AlbumContent>) {
        val now = OffsetDateTime.now()
        val insertMap: List<Map<Field<*>, Any?>> =
            ALBUM_CONTENT.run {
                albumContent.map {
                    mapOf(
                        ID to UUID.randomUUID(),
                        ALBUM_ID to it.albumId,
                        PHOTO_ID to it.photoId,
                        CAPTURED_AT to it.capturedAt,
                        CREATED_AT to now,
                        UPDATED_AT to now,
                    )
                }
            }
        val columns = insertMap.first().keys.toList()
        Flux
            .from(
                dslContext
                    .invoke()
                    .insertInto(ALBUM_CONTENT)
                    .columns(columns)
                    .valuesOfRows(
                        insertMap.map { DSL.row(it.values) },
                    ),
            ).asFlow()
            .collect {}
    }

    override suspend fun deleteBy(albumId: AlbumId) {
        Flux
            .from(
                dslContext
                    .invoke()
                    .deleteFrom(ALBUM_CONTENT)
                    .where(ALBUM_CONTENT.ALBUM_ID.eq(albumId)),
            ).asFlow()
            .collect {}
    }

    override suspend fun deleteByPhotoId(
        albumId: AlbumId,
        photoId: UUID,
    ) {
        Flux
            .from(
                dslContext
                    .invoke()
                    .deleteFrom(ALBUM_CONTENT)
                    .where(ALBUM_CONTENT.ALBUM_ID.eq(albumId).and(ALBUM_CONTENT.PHOTO_ID.eq(photoId))),
            ).asFlow()
            .collect {}
    }
}
