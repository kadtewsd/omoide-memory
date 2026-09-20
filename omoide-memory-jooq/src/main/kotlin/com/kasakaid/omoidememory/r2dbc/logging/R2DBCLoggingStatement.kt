package com.kasakaid.omoidememory.r2dbc.logging

import io.github.oshai.kotlinlogging.KLogger
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import org.reactivestreams.Publisher
import org.slf4j.MDC
import reactor.core.publisher.Flux
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * EventListner が R2DBC 未対応のためやむを得ず自前でログをがんばる
 */
class R2DBCLoggingStatement(
    private val delegate: Statement,
    private val sql: String,
    private val log: KLogger,
    private val mdc: Map<String, String?>,
) : Statement by delegate {
    private val bindsByIndex = LinkedHashMap<Int, Any?>()

    override fun bind(
        index: Int,
        value: Any,
    ): Statement {
        bindsByIndex[index] = value
        delegate.bind(index, value)
        return this
    }

    override fun execute(): Publisher<out Result> {
        // MDC を復元して R2DBC のコンテキストにコピーする。
        // delegate.execute() のストリームが購読（subscribe）されてから完了するまでの間、
        // MDC をスレッドにバインドし続ける
        return Flux.defer {
            val previous = MDC.getCopyOfContextMap()
            if (mdc.isNotEmpty()) MDC.setContextMap(mdc) // 空なら既存の MDC を潰さない
            try {
                log.info { "$sql, ${bindsByIndex.entries.joinToString(prefix = "[", postfix = "]") { (k, v) -> "$k=$v" }}" }
            } finally {
                if (previous != null) MDC.setContextMap(previous) else MDC.clear()
            }
            Flux.from(delegate.execute()) // Mono.from だと複数 Result の2つ目以降を落とすため Flux にしている
        }
    }
}
