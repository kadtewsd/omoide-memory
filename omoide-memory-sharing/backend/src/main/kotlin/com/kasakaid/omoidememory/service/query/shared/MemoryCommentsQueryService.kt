package com.kasakaid.omoidememory.service.query.shared

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.pojos.CommentOmoide
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENTER
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import org.jooq.DatePart
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

@Service
class MemoryCommentsQueryService(
    private val dslContext: DSLGenerator,
) {
    suspend fun <T : Any> getComments(
        contentId: UUID,
        mapper: (CommentOmoide, commenterName: String, commenterIconBase64: String?) -> T,
    ): Flux<T> {
        val mediaFileNameQuery =
            DSL
                .select(SYNCED_OMOIDE_PHOTO.FILE_NAME)
                .from(SYNCED_OMOIDE_PHOTO)
                .where(SYNCED_OMOIDE_PHOTO.ID.eq(contentId))
                .unionAll(
                    DSL
                        .select(SYNCED_OMOIDE_VIDEO.FILE_NAME)
                        .from(SYNCED_OMOIDE_VIDEO)
                        .where(SYNCED_OMOIDE_VIDEO.ID.eq(contentId)),
                )

        return Flux
            .from(
                dslContext
                    .invoke()
                    .select(
                        COMMENT_OMOIDE.asterisk(),
                        COMMENTER.NAME,
                        COMMENTER.ICON,
                    ).from(COMMENT_OMOIDE)
                    .leftJoin(COMMENTER)
                    .on(COMMENT_OMOIDE.COMMENTER_ID.eq(COMMENTER.ID))
                    .where(
                        COMMENT_OMOIDE.FILE_NAME
                            .`in`(mediaFileNameQuery)
                            .or(COMMENT_OMOIDE.FEED_ID.eq(contentId)),
                    ).orderBy(COMMENT_OMOIDE.COMMENTED_AT.asc()),
            ).map { record: Record ->
                val commentPojo = record.into(CommentOmoide::class.java)
                val commenterName = record.get(COMMENTER.NAME, String::class.java) ?: ""
                val commenterIcon = record.get(COMMENTER.ICON, String::class.java)
                mapper(commentPojo, commenterName, commenterIcon)
            }
    }

    suspend fun getCommentCreatedYearMonths(): Mono<List<OffsetDateTime>> {
        val commentedAtYearMonthField = DSL.trunc(COMMENT_OMOIDE.COMMENTED_AT, DatePart.MONTH)
        return Flux
            .from(
                dslContext
                    .invoke()
                    .selectDistinct(commentedAtYearMonthField)
                    .from(COMMENT_OMOIDE)
                    .where(COMMENT_OMOIDE.COMMENTED_AT.isNotNull)
                    .orderBy(commentedAtYearMonthField.desc()),
            ).mapNotNull { record -> record.value1() }
            .collectList()
    }
}
