package com.kasakaid.omoidememory.shared.adapter.filter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class MdcTracerFilter : WebFilter {
    private val log = KotlinLogging.logger {}

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> =
        Mono.deferContextual { ctx ->
            val mdcMap = ctx.getOrDefault<Map<String, String>>("mdc", emptyMap())
            log.info { "MdcMap $mdcMap " }
            chain.filter(exchange)
        }
}
