package com.kasakaid.omoidememory.commentimport.service

import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.infrastructure.CommentRepository
import com.kasakaid.omoidememory.downloader.domain.MediaType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class CommentImportService(
    private val commentRepository: CommentRepository,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun importComment(omoideComment: OmoideComment) {
        val mediaTypeOpt = MediaType.of(omoideComment.fileName)
        if (mediaTypeOpt.isNone()) {
            logger.warn { "Unknown media type for file: ${omoideComment.fileName}. Skipping." }
            return
        }

        val mediaType = mediaTypeOpt.getOrNull()!!

        when (mediaType) {
            MediaType.PHOTO -> {
                commentRepository.insertPhotoComment(omoideComment)
            }

            MediaType.VIDEO -> {
                commentRepository.insertVideoComment(omoideComment)
            }
        }
    }
}
