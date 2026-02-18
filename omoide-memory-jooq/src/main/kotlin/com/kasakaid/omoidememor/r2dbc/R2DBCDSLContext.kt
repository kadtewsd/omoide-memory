package com.kasakaid.omoidememor.r2dbc

import kotlinx.coroutines.currentCoroutineContext
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class R2DBCDSLContext(
    private val dslContext: DSLContext,
) {
    suspend fun get(): DSLContext = currentCoroutineContext().getR2DBCContext()?: dslContext
}