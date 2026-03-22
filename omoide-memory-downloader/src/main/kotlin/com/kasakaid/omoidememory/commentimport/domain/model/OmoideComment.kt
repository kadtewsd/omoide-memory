package com.kasakaid.omoidememory.commentimport.domain.model

import java.time.OffsetDateTime
import java.util.UUID

class OmoideComment(
    val fileName: String,
    val commentBody: String,
    val commenterName: String,
    val commentedAt: OffsetDateTime,
    val mediaType: String,
    val feedId: UUID,
)

interface OmoideCommentRepository {
    suspend fun add(omoideComment: OmoideComment)

    suspend fun exists(omoideComment: OmoideComment): Boolean
}
