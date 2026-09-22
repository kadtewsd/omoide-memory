package com.kasakaid.omoidememory.r2dbc.logging

import com.kasakaid.omoidememory.adapter.filter.MdcFilterKey
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import org.jooq.DSLContext

/**
 * jOOQ + R2DBC で、リクエストの MDC を SQL ログに載せるための拡張。
 *
 * 【なぜ必要か】
 * - jOOQ の ExecuteListener は R2DBC 経路では呼ばれない（未対応。jOOQ #12221 / #13590 / Discussion #14498）。
 *   そのため Listener で MDC を復元する手は使えない。
 * - R2DBC ドライバのスレッド (reactor-tcp-nio-*) では、Filter でセットした MDC の ThreadLocal は空。
 *   また、Statement.execute() の中では Reactor Context も取れなかった。
 *
 * 【やっていること】
 * 1. 呼び出し側のコルーチンの ReactorContext から MDC のスナップショットを取る。
 *    （MDCContext は別のコルーチンには引き継がれないので、ここでは使えない）
 * 2. スナップショットを焼き込んだ R2DBCLoggingConnectionFactory を、このクエリ専用の Configuration にセットする。
 * 3. その値が Connection → Statement へオブジェクト参照で渡り、execute() のログ出力の瞬間だけ MDC に復元される。
 *
 * 【制約】
 * - withMdc() を通さないクエリは MDC なしになる。
 * - MDC が出るのは R2DBCLoggingStatement が出す SQL ログのみ。
 *   ドライバ自身のログや、行を受け取った後の処理のログには出ない。
 * - jOOQ が R2DBC で ExecuteListener に対応したら、この仕組みは Listener に置き換えられる可能性がある。
 */
private val logger = KotlinLogging.logger {}

internal suspend fun DSLContext.withMdc(): DSLContext {
    val snapshot =
        currentCoroutineContext()[ReactorContext]
            ?.context
            ?.getOrDefault<Map<String, String?>>(MdcFilterKey.MDC_DATA_KEY, null)
            ?: emptyMap()
    val cf = configuration().connectionFactory() as R2DBCLoggingConnectionFactory
    logger.debug { "withMdc: cf=${System.identityHashCode(cf)} snapshot=$snapshot" }
    return configuration().derive().set(cf.withMdc(snapshot)).dsl()
}
