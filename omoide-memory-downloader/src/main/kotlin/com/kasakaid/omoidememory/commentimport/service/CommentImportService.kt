package com.kasakaid.omoidememory.commentimport.service

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import com.kasakaid.omoidememory.commentimport.domain.model.FileLine
import com.kasakaid.omoidememory.commentimport.domain.model.FileName
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentRepository
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentedDateFactory
import com.kasakaid.omoidememory.domain.Extension
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

typealias NoneExistenceContentName = String

@Service
class CommentImportService(
    private val commentRepository: OmoideCommentRepository,
    private val omoideMemoryRepository: OmoideMemoryRepository,
) {
    val logger = KotlinLogging.logger {}

    suspend fun importComment(
        fileName: FileName,
        fileLines: Collection<FileLine>,
    ): Option<NoneExistenceContentName> {
        commentRepository.deleteByFileName(fileName)

        fileLines
            .mapNotNull { fileLine ->
                if (!fileLine.isValidSize()) {
                    logger.warn { "フォーマットが正しくない行をスキップします: ${fileLine.line}" }
                    return@mapNotNull null
                }
                val commentedAt =
                    OmoideCommentedDateFactory
                        .create(fileName = fileName, authorParts = fileLine.authorParts)
                        .fold(
                            ifLeft = {
                                throw IllegalStateException("パース不可能なコメントです: $fileName $fileLine $it")
                            },
                            ifRight = { it },
                        )

                OmoideComment(
                    fileName = fileName,
                    commentBody = fileLine.commentBody,
                    commenterName = fileLine.commenterName,
                    commentedAt = commentedAt,
                    mediaType = fileLine.mediaType,
                    feedId = fileLine.mediaId,
                )
            }.forEach { comment ->
                commentRepository.add(comment)
            }

        if (omoideMemoryRepository.existsVideoByFileName(fileName)) {
            return None
        }

        if (omoideMemoryRepository.existsPhotoByFileName(fileName)) {
            return None
        }
        return fileName.some()
    }
}
