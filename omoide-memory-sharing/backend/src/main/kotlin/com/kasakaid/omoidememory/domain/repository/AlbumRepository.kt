package com.kasakaid.omoidememory.domain.repository

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.model.AlbumContent
import java.util.UUID

interface AlbumRepository {
    suspend fun get(albumId: UUID): Album?

    suspend fun save(album: Album): Album

    suspend fun addContent(
        albumId: UUID,
        contentId: UUID,
        content: AlbumContent,
    )

    suspend fun confirm(albumId: UUID)
}
