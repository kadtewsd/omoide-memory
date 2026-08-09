package com.kasakaid.omoidememory.domain.repository

import com.kasakaid.omoidememory.domain.model.Album

interface AlbumRepository {
    suspend fun save(album: Album): Album
}
