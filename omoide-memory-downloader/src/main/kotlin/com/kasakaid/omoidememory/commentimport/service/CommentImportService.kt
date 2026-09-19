package com.kasakaid.omoidememory.commentimport.service

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentRepository
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import org.springframework.stereotype.Service

typealias NoneExistenceContentName = String

@Service
class CommentImportService(
    private val commentRepository: OmoideCommentRepository,
    private val omoideMemoryRepository: OmoideMemoryRepository,
) {
    suspend fun importComment(omoideComment: OmoideComment): Option<NoneExistenceContentName> {
        commentRepository.add(omoideComment)

        if (omoideMemoryRepository.existsVideoByFileName(omoideComment.fileName)) {
            return None
        }

        if (omoideMemoryRepository.existsPhotoByFileName(omoideComment.fileName)) {
            return None
        }
        return omoideComment.fileName.some()
    }

    suspend fun deleteByFileName(fileName: String) {
        commentRepository.deleteByFileName(fileName)
    }
}
