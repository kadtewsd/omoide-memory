package com.kasakaid.omoidememory.commentimport.infrastructure

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.CommenterRecord
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENTER
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class JooqCommenterDao(
    private val dsl: DSLGenerator,
) {
    @Cacheable("commenters")
    suspend fun findAll(): Map<Long, CommenterRecord> =
        COMMENTER.run {
            dsl.invoke().selectFrom(this).asFlow().toList().associateBy {
                it.id!!
            }
        }
}
