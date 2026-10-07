package com.kasakaid.omoidememory.service.command

import com.kasakaid.omoidememory.domain.model.Album
import com.kasakaid.omoidememory.domain.model.AlbumContent
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
        albumRepository.deleteContents(albumId = albumId)
        albumRepository.update(album = album.clearContentsAndChangePeriod(periodFrom = periodFrom, periodTo = periodTo))
    }

    suspend fun addContent(
        albumId: UUID,
        contentId: UUID,
        photoId: UUID,
        capturedAt: OffsetDateTime?,
    ) {
        albumRepository.get(albumId = albumId) ?: throw NotFoundException("Album not found with id: $albumId")
        albumRepository.addContent(
            albumId = albumId,
            contentId = contentId,
            content = AlbumContent(photoId = photoId, capturedAt = capturedAt),
        )
    }

    suspend fun confirm(albumId: UUID) {
        albumRepository.get(albumId = albumId) ?: throw NotFoundException("Album not found with id: $albumId")
        albumRepository.confirm(albumId = albumId)
    }
}
