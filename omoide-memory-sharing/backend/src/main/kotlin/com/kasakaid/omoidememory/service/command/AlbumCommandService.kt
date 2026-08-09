package com.kasakaid.omoidememory.service.command

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.repository.AlbumRepository
import org.springframework.stereotype.Service
import java.util.UUID

class CreateAlbumCommand(
    val albumName: String,
    val photoIds: List<UUID>,
)

@Service
class AlbumCommandService(
    private val albumRepository: AlbumRepository,
) {
    suspend fun createAlbum(command: CreateAlbumCommand): Album {
        val album =
            Album(
                name = command.albumName,
                photoIds = command.photoIds,
            )
        return albumRepository.save(album)
    }
}
