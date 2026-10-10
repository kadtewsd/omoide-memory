package com.kasakaid.omoidememory.domain.repository

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.model.AlbumContent
import com.kasakaid.omoidememory.domain.model.AlbumId

interface AlbumContentsRepository {
    suspend fun fetchByAlbumId(albumId: AlbumId): List<AlbumContent>

    suspend fun add(content: AlbumContent): AlbumContent

    suspend fun add(albumContents: List<AlbumContent>): List<AlbumContent>

    suspend fun deleteBy(albumId: AlbumId)
}
