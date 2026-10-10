package com.kasakaid.omoidememory.domain.repository

import com.kasakaid.omoidememory.domain.model.AlbumContent
import com.kasakaid.omoidememory.domain.model.AlbumId
import java.util.UUID

interface AlbumContentsRepository {
    suspend fun fetchByAlbumId(albumId: AlbumId): List<AlbumContent>

    suspend fun add(content: AlbumContent): AlbumContent

    suspend fun add(albumContents: List<AlbumContent>): List<AlbumContent>

    suspend fun deleteBy(albumId: AlbumId)

    suspend fun deleteByPhotoId(
        albumId: AlbumId,
        photoId: UUID,
    )
}
