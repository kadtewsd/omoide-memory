package com.kasakaid.omoidememory.service.command

import com.kasakaid.omoidememory.domain.model.AlbumId
import com.kasakaid.omoidememory.domain.repository.AlbumContentId
import com.kasakaid.omoidememory.domain.repository.AlbumContentsRepository
import com.kasakaid.omoidememory.service.query.album.AlbumQueryService
import com.kasakaid.omoidememory.service.query.shared.memoryfeed.MemoryFeedDto
import com.kasakaid.omoidememory.shared.adapter.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AlbumContentReplaceService(
    private val albumContentsRepository: AlbumContentsRepository,
    private val albumCommandService: AlbumCommandService,
    private val albumQueryService: AlbumQueryService,
) {
    suspend fun replaceContent(
        albumId: AlbumId,
        albumContentId: AlbumContentId,
    ): MemoryFeedDto {
        albumContentsRepository.deleteBy(id = albumContentId)
        val candidates = albumQueryService.getRandomPhotosForAlbum(albumId = albumId, count = 1)
        val newPhoto = candidates.firstOrNull() ?: throw NotFoundException("No replacement photo found for album: $albumId")
        albumCommandService.addContent(
            albumId = albumId,
            photoId = newPhoto.id!!,
            capturedAt = newPhoto.captureTime,
        )
        return newPhoto
    }
}
