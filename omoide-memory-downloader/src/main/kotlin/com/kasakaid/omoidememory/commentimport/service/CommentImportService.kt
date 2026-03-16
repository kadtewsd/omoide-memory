package com.kasakaid.omoidememory.commentimport.service

import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.infrastructure.CommentRepository
import com.kasakaid.omoidememory.commentimport.infrastructure.CommenterRepository
import com.kasakaid.omoidememory.downloader.domain.MediaType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class CommentImportService(
    private val commentRepository: CommentRepository,
    private val commenterRepository: CommenterRepository,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun importComment(omoideComment: OmoideComment) {
        val mediaTypeOpt = MediaType.of(omoideComment.fileName)
        if (mediaTypeOpt.isNone()) {
            logger.warn { "Unknown media type for file: ${omoideComment.fileName}. Skipping." }
            return
        }

        val mediaType = mediaTypeOpt.getOrNull()!!

        val commenterId = commenterRepository.findIdByName(omoideComment.commenterName)
        if (commenterId == null) {
            logger.warn { "Commenter not found in database: ${omoideComment.commenterName}. Saving with commenter_id = null." }
        }

        when (mediaType) {
            MediaType.PHOTO -> {
                val photoId = commentRepository.findPhotoIdByFileName(omoideComment.fileName)
                if (photoId == null) {
                    logger.warn { "Photo not found in database: ${omoideComment.fileName}. Skipping." }
                    return
                }
                val nextSeq = commentRepository.getNextPhotoCommentSeq(photoId)
                commentRepository.insertPhotoComment(
                    photoId = photoId,
                    commenterId = commenterId,
                    commentSeq = nextSeq,
                    commentBody = omoideComment.commentBody,
                )
            }

            MediaType.VIDEO -> {
                val videoId = commentRepository.findVideoIdByFileName(omoideComment.fileName)
                if (videoId == null) {
                    logger.warn { "Video not found in database: ${omoideComment.fileName}. Skipping." }
                    return
                }
                val nextSeq = commentRepository.getNextVideoCommentSeq(videoId)
                commentRepository.insertVideoComment(
                    videoId = videoId,
                    commenterId = commenterId,
                    commentSeq = nextSeq,
                    commentBody = omoideComment.commentBody,
                )
            }
        }
    }
}
