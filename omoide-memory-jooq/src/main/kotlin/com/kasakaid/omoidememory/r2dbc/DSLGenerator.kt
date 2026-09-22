package com.kasakaid.omoidememory.r2dbc

import com.kasakaid.omoidememory.r2dbc.logging.withMdc
import org.jooq.DSLContext

/**
 * DSLContext を生成する。
 * この際、MDCのコンテキストをセットした DSLContext を生成する
 */
class DSLGenerator(
    private val dslContext: DSLContext,
) {
    suspend fun invoke(): DSLContext = dslContext.withMdc()
}
