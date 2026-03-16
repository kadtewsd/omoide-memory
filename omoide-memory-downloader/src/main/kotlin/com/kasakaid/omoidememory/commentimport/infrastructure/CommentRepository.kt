package com.kasakaid.omoidememory.commentimport.infrastructure

import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENTER
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE_VIDEO
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class CommentRepository(
    private val dslContext: DSLContext,
) {
    private var commenters: Map<String, Number>? = null
    private val mutex = Mutex()

    private suspend fun getCommenters(): Map<String, Number> =
        commenters ?: mutex.withLock {
            // ロック取得後に再チェック（二重初期化防止）
            commenters ?: dslContext
                .selectFrom(COMMENTER)
                .asFlow()
                .toList()
                .associate { it.name to it.id!! }
                .also { commenters = it }
        }

    suspend fun insertPhotoComment(omoideComment: OmoideComment) {
        COMMENT_OMOIDE_PHOTO.run {
            dslContext
                .insertInto(COMMENT_OMOIDE_PHOTO)
                .set(ID, MyUUIDGenerator.generateUUIDv7())
                .set(COMMENTER_ID, getCommenters()[omoideComment.commenterName]?.toLong()!!)
                .set(COMMENT_BODY, omoideComment.commentBody)
                .set(COMMENTED_AT, OffsetDateTime.now())
                .set(CREATED_AT, OffsetDateTime.now())
                .set(CREATED_BY, "comment-import")
                .awaitSingle()
        }
    }

    suspend fun insertVideoComment(omoideComment: OmoideComment) {
        COMMENT_OMOIDE_VIDEO.run {
            dslContext
                .insertInto(COMMENT_OMOIDE_VIDEO)
                .set(ID, MyUUIDGenerator.generateUUIDv7())
                .set(COMMENTER_ID, getCommenters()[omoideComment.commenterName]?.toLong()!!)
                .set(COMMENT_BODY, omoideComment.commentBody)
                .set(COMMENTED_AT, OffsetDateTime.now())
                .set(CREATED_AT, OffsetDateTime.now())
                .set(CREATED_BY, "comment-import")
                .awaitSingle()
        }
    }
}
