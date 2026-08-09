package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.repository.AlbumRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.ALBUM_PHOTO
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class AlbumRepositoryImpl(
    private val dslContext: DSLContext,
) : AlbumRepository {
    override suspend fun save(album: Album): Album {
        val records =
            album.photoIds.map { photoId ->
                dslContext
                    .insertInto(ALBUM_PHOTO)
                    .set(ALBUM_PHOTO.ID, UUID.randomUUID())
                    .set(ALBUM_PHOTO.ALBUM_ID, album.id)
                    .set(ALBUM_PHOTO.ALBUM_NAME, album.name)
                    .set(ALBUM_PHOTO.PHOTO_ID, photoId)
                    .set(ALBUM_PHOTO.FAMILY_ID, album.familyId)
                    .set(ALBUM_PHOTO.CREATED_AT, OffsetDateTime.now())
            }

        Flux
            .concat(records.map { Flux.from(it) })
            .collectList()
            .toFuture()
            .get()
        return album
    }
}
