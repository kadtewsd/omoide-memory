package com.kasakaid.omoidememory.service.command

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.repository.AlbumRepository
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.util.UUID

class CreateAlbumCommand(
    val albumName: String,
    val photoIds: List<UUID>,
)

@Service
class AlbumCommandService(
    private val environment: Environment,
    private val albumRepository: AlbumRepository,
) {
    suspend fun createAlbum(command: CreateAlbumCommand): Album {
        val album =
            Album(
                id = UUID.randomUUID(),
                name = command.albumName,
                photoIds = command.photoIds,
                familyId =
                    environment.getProperty("omoide.family.id")
                        ?: throw IllegalStateException("Environment property family.id must be set"),
            )
        return albumRepository.save(album)
    }
}
