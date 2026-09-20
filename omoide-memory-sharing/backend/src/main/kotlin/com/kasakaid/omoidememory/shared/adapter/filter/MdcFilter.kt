package com.kasakaid.omoidememory.shared.adapter.filter

import com.kasakaid.omoidememory.adapter.filter.MdcFilterKey
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context
import java.net.URLDecoder
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcFilter : CoWebFilter() {
    private val logger = KotlinLogging.logger {}

    override suspend fun filter(
        exchange: ServerWebExchange,
        chain: CoWebFilterChain,
    ) {
        val request: ServerHttpRequest = exchange.request
        logger.info { "MdcFilter $request" }
        val map =
            mapOf(
                "requestId" to UUID.randomUUID().toString(),
                "requestUrl" to URLDecoder.decode(request.uri.toString(), Charsets.UTF_8),
                "requestMethod" to request.method.name(),
                "userAgent" to (request.headers.getFirst(HttpHeaders.USER_AGENT) ?: "-"),
            )
        return withContext(
            // MDCContext は ThreadContextElement で、コルーチンが再開する瞬間だけMDCのThreadLocalを復元します。裏を返すと、chain.filter(exchange) を awaitSingleOrNull() 等で待っている間、Reactor自身が内部で回している Mono/Flux のオペレータ（doOnNext、doFinally など）の実行そのものはコルーチンの「再開」ではないので、MDCContext の管理範囲外です。つまり:
            // この部分は MDCContext の管理下にないので、MDCが空になります。以前試していた「Reactor Context に載せて Automatic Context Propagation で復元する」方式が本来カバーしていたのはまさにこの領域で、MDCContext はその代替ではなく別レイヤーの仕組みだった、というのが正確な理解です。
            // MdcCoWebFilter の中で、MDCContext（コルーチン用）と ReactorContext（Reactor Context用）を 両方 withContext に積めば、両方の世界をカバーできます。
            MDCContext(map) + ReactorContext(Context.of(MdcFilterKey.MDC_DATA_KEY, map)),
        ) {
            chain.filter(exchange)
        }
    }
}
