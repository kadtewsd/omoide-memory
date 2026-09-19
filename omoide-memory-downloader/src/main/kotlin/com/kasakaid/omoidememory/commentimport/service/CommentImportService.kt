package com.kasakaid.omoidememory.commentimport.service

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import com.kasakaid.omoidememory.commentimport.domain.model.FileName
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentRepository
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

typealias NoneExistenceContentName = String

@Service
class CommentImportService(
    private val commentRepository: OmoideCommentRepository,
    private val omoideMemoryRepository: OmoideMemoryRepository,
) {
    val logger = KotlinLogging.logger {}

    suspend fun importComment(
        fileName: FileName,
        omoideComments: Collection<OmoideComment>,
    ): Option<NoneExistenceContentName> {
        commentRepository.deleteByFileName(fileName)

        omoideComments.forEach { comment ->
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
