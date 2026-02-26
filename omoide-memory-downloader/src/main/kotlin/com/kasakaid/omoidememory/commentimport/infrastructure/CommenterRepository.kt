package com.kasakaid.omoidememory.commentimport.infrastructure

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.COMMENTER
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class CommenterRepository(
    private val dslContext: DSLContext,
) {
    suspend fun findIdByName(name: String): Long? =
        dslContext
            .selectFrom(COMMENTER)
            .where(COMMENTER.NAME.eq(name))
            .awaitFirstOrNull()
            ?.id
}
