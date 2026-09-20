package com.kasakaid.omoidememory.service.query.shared.memoryfeed

import com.kasakaid.omoidememory.domain.model.CommentCount
import com.kasakaid.omoidememory.domain.model.ContentName
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENT_OMOIDE
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service

@Service
class CommentCountQuery(
    private val dslContext: DSLContext,
) {
    suspend fun fetch(fileNames: Set<ContentName>): Map<ContentName, CommentCount> =
        dslContext
            .select(COMMENT_OMOIDE.FILE_NAME, DSL.count())
            .from(COMMENT_OMOIDE)
            .where(COMMENT_OMOIDE.FILE_NAME.`in`(fileNames))
            .groupBy(COMMENT_OMOIDE.FILE_NAME)
            .asFlow()
            .toList()
            .associate { (it.value1() ?: "") to (it.value2() ?: 0) }
}
