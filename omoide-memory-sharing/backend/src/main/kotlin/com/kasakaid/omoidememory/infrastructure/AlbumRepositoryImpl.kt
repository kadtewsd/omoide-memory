package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.model.AlbumStatus
import com.kasakaid.omoidememory.domain.repository.AlbumRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.Field
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class AlbumRepositoryImpl(
    private val dslContext: DSLGenerator,
    private val contentsRepo: JooqAlbumContentsRepository,
) : AlbumRepository {
    private fun updateAlbumMap(album: Album): Map<Field<*>, Any?> =
        ALBUM.run {
            mapOf(
                NAME to album.name,
                STATUS to album.status.name,
                PERIOD_FROM to album.periodFrom,
                PERIOD_TO to album.periodTo,
                UPDATED_AT to OffsetDateTime.now(),
            )
        }

    private fun albumInsertMap(album: Album): Map<Field<*>, Any?> =
        ALBUM.run {
            return mapOf(
                ID to album.id,
                FAMILY_ID to album.familyId,
                CREATED_AT to OffsetDateTime.now(),
            ) + updateAlbumMap(album)
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

        return Album(
            id = albumRecord.id,
            name = albumRecord.name,
            status = albumRecord.status?.let { AlbumStatus.valueOf(it) } ?: AlbumStatus.DRAFT,
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
                    .set(
                        updateAlbumMap(album),
                    ).where(ALBUM.ID.eq(album.id)),
            ).asFlow()
            .collect {}
    }
}
