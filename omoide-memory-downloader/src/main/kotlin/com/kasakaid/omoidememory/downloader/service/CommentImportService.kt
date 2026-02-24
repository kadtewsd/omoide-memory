package com.kasakaid.omoidememory.downloader.service

import com.kasakaid.omoidememory.downloader.domain.MediaType
import com.kasakaid.omoidememory.downloader.domain.ParsedComment
import com.kasakaid.omoidememory.infrastructure.CommentRepository
import com.kasakaid.omoidememory.infrastructure.CommenterRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class CommentImportService(
    private val commentRepository: CommentRepository,
    private val commenterRepository: CommenterRepository,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun importComment(parsedComment: ParsedComment) {
        val mediaTypeOpt = MediaType.of(parsedComment.fileName)
        if (mediaTypeOpt.isNone()) {
            logger.warn { "Unknown media type for file: ${parsedComment.fileName}. Skipping." }
            return
        }

        val mediaType = mediaTypeOpt.getOrNull()!!

        val commenterId = commenterRepository.findIdByName(parsedComment.commenterName)
        if (commenterId == null) {
            logger.warn { "Commenter not found in database: ${parsedComment.commenterName}. Saving with commenter_id = null." }
        }

        when (mediaType) {
            MediaType.PHOTO -> {
                val photoId = commentRepository.findPhotoIdByFileName(parsedComment.fileName)
                if (photoId == null) {
                    logger.warn { "Photo not found in database: ${parsedComment.fileName}. Skipping." }
                    return
                }
                val nextSeq = commentRepository.getNextPhotoCommentSeq(photoId)
                commentRepository.insertPhotoComment(
                    photoId = photoId,
                    commenterId = commenterId,
                    commentSeq = nextSeq,
                    commentBody = parsedComment.commentBody,
                )
            }

            MediaType.VIDEO -> {
                val videoId = commentRepository.findVideoIdByFileName(parsedComment.fileName)
                if (videoId == null) {
                    logger.warn { "Video not found in database: ${parsedComment.fileName}. Skipping." }
                    return
                }
                val nextSeq = commentRepository.getNextVideoCommentSeq(videoId)
                commentRepository.insertVideoComment(
                    videoId = videoId,
                    commenterId = commenterId,
                    commentSeq = nextSeq,
                    commentBody = parsedComment.commentBody,
                )
            }
        }
    }
}
