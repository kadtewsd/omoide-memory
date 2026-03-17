package com.kasakaid.omoidememory.commentimport.service

import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class CommentImportService(
    private val commentRepository: OmoideCommentRepository,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun importComment(omoideComment: OmoideComment) {
        if (commentRepository.exists(omoideComment)) {
            logger.info { "すでに登録されているコメントです。スキップします: ${omoideComment.fileName} - ${omoideComment.commentBody}" }
            return
        }
        commentRepository.add(omoideComment)
    }
}
