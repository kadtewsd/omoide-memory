package com.kasakaid.omoidememory.commentimport.service

import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentRepository
import org.springframework.stereotype.Service

@Service
class CommentImportService(
    private val commentRepository: OmoideCommentRepository,
) {
    suspend fun importComment(omoideComment: OmoideComment) {
        commentRepository.add(omoideComment)
    }

    suspend fun deleteByFileName(fileName: String) {
        commentRepository.deleteByFileName(fileName)
    }
}
