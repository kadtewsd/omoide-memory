package com.kasakaid.omoidememory.domain.repository

import com.kasakaid.omoidememory.domain.model.Album
import java.util.UUID

interface AlbumRepository {
    suspend fun get(albumId: UUID): Album?

    suspend fun save(album: Album): Album

    suspend fun update(
        album: Album,
        existence: Album,
    ): Album
}
