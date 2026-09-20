package com.kasakaid.omoidememory.shared.adapter.filter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.function.Consumer

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100) // MdcFilterより後、なるべく早く
class RequestLoggingFilter : WebFilter {
    private val logger = KotlinLogging.logger {}

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val request: ServerHttpRequest = exchange.request
        val url: String = URLDecoder.decode(request.uri.toString(), Charsets.UTF_8)

        // Get and log the request body
        return DataBufferUtils
            .join(exchange.request.body)
            .doOnNext(
                Consumer { buffer: DataBuffer ->
                    logger.info { "Request URL: $url 開始" }
                    val bytes = ByteArray(buffer.readableByteCount())
                    buffer.read(bytes)
                    val body = String(bytes, StandardCharsets.UTF_8)
                    logger.info { "Request Body: $body" }
                },
            ).doOnCancel({
                logger.debug { "Request body $url logging cancelled" }
            })
            .doFinally(
                {
                    logger.info { "Request $url 完了。signal: ${it.name}" }
                },
            ).then(chain.filter(exchange))
    }
}
