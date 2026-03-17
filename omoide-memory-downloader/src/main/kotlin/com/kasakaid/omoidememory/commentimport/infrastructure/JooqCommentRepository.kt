package com.kasakaid.omoidememory.commentimport.infrastructure

import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENTER
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class JooqCommentRepository(
    private val dslContext: DSLContext,
) : OmoideCommentRepository {
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

    override suspend fun add(omoideComment: OmoideComment) {
        COMMENT_OMOIDE.run {
            dslContext
                .insertInto(COMMENT_OMOIDE)
                .set(ID, MyUUIDGenerator.generateUUIDv7())
                .set(FILE_NAME, omoideComment.fileName)
                .set(COMMENTER_ID, getCommenters()[omoideComment.commenterName]?.toLong()!!)
                .set(COMMENT_BODY, omoideComment.commentBody)
                .set(COMMENTED_AT, omoideComment.commentedAt)
                .set(CREATED_AT, OffsetDateTime.now())
                .set(CREATED_BY, "comment-import")
                .awaitSingle()
        }
    }

    override suspend fun exists(omoideComment: OmoideComment): Boolean =
        COMMENT_OMOIDE.run {
            return (
                dslContext
                    .selectCount()
                    .from(this)
                    .where(FILE_NAME.eq(omoideComment.fileName))
                    .and(COMMENT_BODY.eq(omoideComment.commentBody))
                    .awaitFirstOrNull()
                    ?.value1() ?: 0
            ) > 0
        }
}
