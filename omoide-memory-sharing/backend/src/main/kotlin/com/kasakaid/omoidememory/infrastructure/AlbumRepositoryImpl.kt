package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.model.AlbumContent
import com.kasakaid.omoidememory.domain.model.AlbumStatus
import com.kasakaid.omoidememory.domain.repository.AlbumRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM_CONTENT
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
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
                NAME to album.name,
                STATUS to album.status.name,
                PERIOD_FROM to album.periodFrom,
                PERIOD_TO to album.periodTo,
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

        val contents =
            dslContext
                .invoke()
                .select(ALBUM_CONTENT.PHOTO_ID, ALBUM_CONTENT.CAPTURED_AT)
                .from(ALBUM_CONTENT)
                .where(ALBUM_CONTENT.ALBUM_ID.eq(albumId))
                .orderBy(ALBUM_CONTENT.CAPTURED_AT.asc().nullsLast(), ALBUM_CONTENT.ID.asc())
                .asFlow()
                .toList()
                .map { AlbumContent(photoId = it.value1()!!, capturedAt = it.value2()) }

        return Album(
            id = albumRecord.id,
            name = albumRecord.name,
            status = albumRecord.status?.let { AlbumStatus.valueOf(it) } ?: AlbumStatus.DRAFT,
            contents = contents,
            familyId = albumRecord.familyId,
            periodFrom = albumRecord.periodFrom!!,
            periodTo = albumRecord.periodTo!!,
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
        return album
    }

    override suspend fun update(album: Album) {
        Flux
            .from(
                dslContext
                    .invoke()
                    .update(ALBUM)
                    .set(ALBUM.NAME, album.name)
                    .set(ALBUM.STATUS, album.status.name)
                    .set(ALBUM.PERIOD_FROM, album.periodFrom)
                    .set(ALBUM.PERIOD_TO, album.periodTo)
                    .set(ALBUM.UPDATED_AT, OffsetDateTime.now())
                    .where(ALBUM.ID.eq(album.id)),
            ).asFlow()
            .collect {}
    }

    override suspend fun addContent(
        albumId: UUID,
        contentId: UUID,
        content: AlbumContent,
    ) {
        val insertMap: Map<Field<*>, Any?> =
            ALBUM_CONTENT.run {
                mapOf(
                    ID to contentId,
                    ALBUM_ID to albumId,
                    PHOTO_ID to content.photoId,
                    CAPTURED_AT to content.capturedAt,
                    CREATED_AT to OffsetDateTime.now(),
                    UPDATED_AT to OffsetDateTime.now(),
                )
            }
        val columns = insertMap.keys.toList()
        Flux
            .from(
                dslContext
                    .invoke()
                    .insertInto(ALBUM_CONTENT)
                    .columns(columns)
                    .values(DSL.row(columns.map { insertMap[it] }))
                    .onConflict(ALBUM_CONTENT.ID)
                    .doUpdate()
                    .set(ALBUM_CONTENT.PHOTO_ID, content.photoId)
                    .set(ALBUM_CONTENT.CAPTURED_AT, content.capturedAt)
                    .set(ALBUM_CONTENT.UPDATED_AT, OffsetDateTime.now()),
            ).asFlow()
            .collect {}
    }

    override suspend fun deleteContents(albumId: UUID) {
        Flux
            .from(
                dslContext
                    .invoke()
                    .deleteFrom(ALBUM_CONTENT)
                    .where(ALBUM_CONTENT.ALBUM_ID.eq(albumId)),
            ).asFlow()
            .collect {}
    }

    override suspend fun confirm(albumId: UUID) {
        Flux
            .from(
                dslContext
                    .invoke()
                    .update(ALBUM)
                    .set(ALBUM.STATUS, AlbumStatus.CONFIRMED.name)
                    .set(ALBUM.UPDATED_AT, OffsetDateTime.now())
                    .where(ALBUM.ID.eq(albumId)),
            ).asFlow()
            .collect {}
    }
}
