package com.kasakaid.omoidememory.service.query.shared

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENTER
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

@Service
class MemoryCommentsQueryService(
    private val dslContext: DSLContext,
) {
    suspend fun <T : Any> getComments(
        feedId: UUID,
        mapper: (CommentOmoide, commenterName: String, commenterIconBase64: String?) -> T,
    ): Flux<T> =
        Flux
            .from(
                dslContext
                    .select(
                        COMMENT_OMOIDE.asterisk(),
                        COMMENTER.NAME,
                        COMMENTER.ICON,
                    ).from(COMMENT_OMOIDE)
                    .leftJoin(COMMENTER)
                    .on(COMMENT_OMOIDE.COMMENTER_ID.eq(COMMENTER.ID))
                    .where(COMMENT_OMOIDE.FEED_ID.eq(feedId))
                    .orderBy(COMMENT_OMOIDE.COMMENTED_AT.asc()),
            ).map { record: Record ->
                val commentPojo = record.into(CommentOmoide::class.java)
                val commenterName = record.get(COMMENTER.NAME, String::class.java) ?: ""
                val commenterIcon = record.get(COMMENTER.ICON, String::class.java)
                mapper(commentPojo, commenterName, commenterIcon)
            }

    fun getCommentCreatedYearMonths(): Mono<List<OffsetDateTime>> =
        Flux
            .from(
                dslContext
                    .select(COMMENT_OMOIDE.COMMENTED_AT)
                    .from(COMMENT_OMOIDE)
                    .where(COMMENT_OMOIDE.COMMENTED_AT.isNotNull),
            ).mapNotNull { record -> record.value1() }
            .collectList()
            .map { times ->
                times.sortedDescending()
            }
}
