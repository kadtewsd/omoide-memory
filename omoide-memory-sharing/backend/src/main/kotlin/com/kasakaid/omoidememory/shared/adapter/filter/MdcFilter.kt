package com.kasakaid.omoidememory.shared.adapter.filter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.URLDecoder
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcFilter : WebFilter {
    private val logger = KotlinLogging.logger {}

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val request: ServerHttpRequest = exchange.request
        logger.info { "MdcFilter $request" }
        return chain
            .filter(exchange)
            .contextWrite { ctx ->
                ctx.put(
                    "mdc",
                    mapOf(
                        "requestId" to UUID.randomUUID().toString(),
                        "requestUrl" to URLDecoder.decode(request.uri.toString(), Charsets.UTF_8),
                        "requestMethod" to request.method.name(),
                        "userAgent" to (request.headers.getFirst(HttpHeaders.USER_AGENT) ?: "-"),
                    ),
                )
            }
        // ← contextWriteはこのオペレータより 前（Subscribe視点では後、購読が伝播していく方向） の処理から Context を読めるようにする。
        // Subscriber は下から上に向かって subscribe() が伝播し、その際に Context も一緒に上流へ伝わるので、これより「上流」だけに見える
    }
}
