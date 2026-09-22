package com.kasakaid.omoidememory.commentimport.infrastructure

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.CommenterRecord
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENTER
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import org.jooq.DSLContext
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class JooqCommenterDao(
    private val dsl: DSLContext,
) {
    @Cacheable("commenters")
    suspend fun findAll(): Map<Long, CommenterRecord> =
        COMMENTER.run {
            dsl.selectFrom(this).asFlow().toList().associateBy {
                it.id!!
            }
        }
}
