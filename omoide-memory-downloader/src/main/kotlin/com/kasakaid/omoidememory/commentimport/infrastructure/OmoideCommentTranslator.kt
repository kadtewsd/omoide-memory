package com.kasakaid.omoidememory.commentimport.infrastructure

import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.CommentOmoideRecord
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.CommenterRecord

object OmoideCommentTranslator {
    /**
     * コメントの名前を取得する
     */
    fun translate(
        record: CommentOmoideRecord,
        commenterRecord: Map<Long, CommenterRecord>,
    ): OmoideComment =
        OmoideComment(
            feedId = record.feedId,
            fileName = record.fileName,
            mediaType = record.mediaType,
            commentBody = record.commentBody!!,
            commenterName = commenterRecord[record.commenterId]!!.name,
            commentedAt = record.commentedAt!!,
        )
}
