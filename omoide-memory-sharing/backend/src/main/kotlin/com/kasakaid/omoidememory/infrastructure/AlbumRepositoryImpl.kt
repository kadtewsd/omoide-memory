package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.model.AlbumStatus
import com.kasakaid.omoidememory.domain.repository.AlbumRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM_CONTENT
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class AlbumRepositoryImpl(
    private val dslContext: DSLGenerator,
) : AlbumRepository {
    private fun albumInsertMap(album: Album): Map<Field<*>, Any?> =
        ALBUM.run {
            mapOf(
                ID to album.id,
                FAMILY_ID to album.familyId,
                CREATED_AT to OffsetDateTime.now(),
            ) + albumUpdateMap(album)
        }

    private fun albumUpdateMap(album: Album): Map<Field<*>, Any?> =
        ALBUM.run {
            mapOf(
                NAME to album.name,
                STATUS to album.status.name,
                UPDATED_AT to OffsetDateTime.now(),
            )
        }

    private fun albumContentInsertMap(
        album: Album,
        photoId: UUID,
    ): Map<Field<*>, Any?> =
        ALBUM_CONTENT.run {
            mapOf(
                ID to UUID.randomUUID(),
                ALBUM_ID to album.id,
                PHOTO_ID to photoId,
                CREATED_AT to OffsetDateTime.now(),
                UPDATED_AT to OffsetDateTime.now(),
            )
        }

    override suspend fun get(albumId: UUID): Album? {
        val albumRecord =
            dslContext
                .invoke()
                .selectFrom(ALBUM)
                .where(ALBUM.ID.eq(albumId))
                .asFlow()
                .toList()
                .firstOrNull() ?: return null

        val photoIds =
            dslContext
                .invoke()
                .select(ALBUM_CONTENT.PHOTO_ID)
                .from(ALBUM_CONTENT)
                .where(ALBUM_CONTENT.ALBUM_ID.eq(albumId))
                .asFlow()
                .toList()
                .map { it.value1()!! }

        return Album(
            id = albumRecord.id,
            name = albumRecord.name,
            status = albumRecord.status?.let { AlbumStatus.valueOf(it) } ?: AlbumStatus.DRAFT,
            photoIds = photoIds,
            familyId = albumRecord.familyId,
        )
    }

    override suspend fun save(album: Album): Album {
        Flux
            .from(
                dslContext
                    .invoke()
                    .insertInto(ALBUM)
                    .set(albumInsertMap(album = album)),
            ).asFlow()
            .collect {}

        val contentInsertMaps = album.photoIds.map { photoId -> albumContentInsertMap(album = album, photoId = photoId) }
        if (contentInsertMaps.isNotEmpty()) {
            val columns = contentInsertMaps.first().keys.toList()
            Flux
                .from(
                    dslContext
                        .invoke()
                        .insertInto(ALBUM_CONTENT)
                        .columns(columns)
                        .valuesOfRows(
                            contentInsertMaps.map { insertMap ->
                                DSL.row(columns.map { column -> insertMap[column] })
                            },
                        ),
                ).asFlow()
                .collect {}
        }

        return album
    }

    override suspend fun update(
        album: Album,
        existence: Album,
    ): Album {
        Flux
            .from(
                dslContext
                    .invoke()
                    .update(ALBUM)
                    .set(albumUpdateMap(album = album))
                    .where(ALBUM.ID.eq(album.id)),
            ).asFlow()
            .collect {}

        val photoIdsToDelete = existence.photoIds - album.photoIds.toSet()
        if (photoIdsToDelete.isNotEmpty()) {
            Flux
                .from(
                    dslContext
                        .invoke()
                        .deleteFrom(ALBUM_CONTENT)
                        .where(
                            ALBUM_CONTENT.ALBUM_ID
                                .eq(album.id)
                                .and(ALBUM_CONTENT.PHOTO_ID.`in`(photoIdsToDelete)),
                        ),
                ).asFlow()
                .collect {}
        }

        val photoIdsToInsert = album.photoIds - existence.photoIds.toSet()
        if (photoIdsToInsert.isNotEmpty()) {
            val contentInsertMaps = photoIdsToInsert.map { photoId -> albumContentInsertMap(album = album, photoId = photoId) }
            val columns = contentInsertMaps.first().keys.toList()
            Flux
                .from(
                    dslContext
                        .invoke()
                        .insertInto(ALBUM_CONTENT)
                        .columns(columns)
                        .valuesOfRows(
                            contentInsertMaps.map { insertMap ->
                                DSL.row(columns.map { column -> insertMap[column] })
                            },
                        ),
                ).asFlow()
                .collect {}
        }

        return album
    }
}
