package com.kasakaid.omoidememory.service.command

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.model.AlbumContent
import com.kasakaid.omoidememory.domain.repository.AlbumContentsRepository
import com.kasakaid.omoidememory.domain.repository.AlbumRepository
import com.kasakaid.omoidememory.shared.adapter.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Transactional
class AlbumCommandService(
    private val albumRepository: AlbumRepository,
    private val albumContentsRepository: AlbumContentsRepository,
) {
    suspend fun createAlbum(
        albumName: String,
        familyId: String,
    ): Album =
        albumRepository.save(
            album =
                Album.initial(
                    name = albumName,
                    familyId = familyId,
                ),
        )

    suspend fun clearContentsAndUpdatePeriod(
        albumId: UUID,
        periodFrom: LocalDate,
        periodTo: LocalDate,
    ) {
        val album = albumRepository.get(albumId = albumId) ?: throw NotFoundException("Album not found with id: $albumId")
        albumContentsRepository.deleteBy(albumId = albumId)
        albumRepository.update(album = album.clearContentsAndChangePeriod(periodFrom = periodFrom, periodTo = periodTo))
    }

    suspend fun addContent(
        albumId: UUID,
        photoId: UUID,
        capturedAt: OffsetDateTime?,
    ) {
        albumContentsRepository.add(
            AlbumContent(
                albumId = albumId,
                photoId = photoId,
                capturedAt = capturedAt,
            ),
        )
    }

    suspend fun confirm(albumId: UUID) {
        val album = albumRepository.get(albumId = albumId) ?: throw NotFoundException("Album not found with id: $albumId")
        albumRepository.update(album.confirm())
    }
}
