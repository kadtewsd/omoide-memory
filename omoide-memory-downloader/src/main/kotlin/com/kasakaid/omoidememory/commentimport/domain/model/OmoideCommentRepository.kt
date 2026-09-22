package com.kasakaid.omoidememory.commentimport.domain.model

interface OmoideCommentRepository {
    suspend fun add(omoideComment: OmoideComment)

    suspend fun deleteByFileName(fileName: FileName)

    suspend fun findByFileName(fileName: FileName): List<OmoideComment>

    suspend fun findByFileNameLike(fileName: FileName): List<OmoideComment>
}
