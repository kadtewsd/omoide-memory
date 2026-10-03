package com.kasakaid.omoidememory.service.command

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.repository.AlbumRepository
import com.kasakaid.omoidememory.shared.adapter.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AlbumCommandService(
    private val albumRepository: AlbumRepository,
) {
    suspend fun createAlbum(
        albumName: String,
        photoIds: List<UUID>,
        familyId: String,
    ): Album {
        val album =
            Album(
                id = UUID.randomUUID(),
                name = albumName,
                photoIds = photoIds,
                familyId = familyId,
            )
        return albumRepository.save(album = album)
    }

    suspend fun updateAlbum(
        albumId: UUID,
        albumName: String,
        photoIds: List<UUID>,
    ): Album {
        val album =
            albumRepository.get(albumId = albumId)
                ?: throw NotFoundException("Album not found with id: $albumId")
        val renewed =
            album.renew(
                name = albumName,
                photoIds = photoIds,
            )
        return albumRepository.update(album = renewed, existence = album)
    }
}
