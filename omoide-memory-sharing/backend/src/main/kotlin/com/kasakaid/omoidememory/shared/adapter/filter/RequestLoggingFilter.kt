package com.kasakaid.omoidememory.shared.adapter.filter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
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

        return DataBufferUtils
            .join(request.body)
            .defaultIfEmpty(exchange.response.bufferFactory().wrap(ByteArray(0)))
            .flatMap { buffer ->
                logger.info { "Request URL: $url 開始" }

                val bytes = ByteArray(buffer.readableByteCount())
                buffer.read(bytes)
                DataBufferUtils.release(buffer) // 読み取り後は明示的に解放する

                val body = String(bytes, StandardCharsets.UTF_8)
                logger.info { "Request Body: $body" }

                // 読み取ったバイト列から body を再生可能な形で差し替えたリクエストを作る
                val decoratedRequest =
                    object : ServerHttpRequestDecorator(request) {
                        override fun getBody(): Flux<DataBuffer> =
                            Flux.defer {
                                // 呼ばれるたびに新しい DataBuffer を作る（1回しかsubscribeされない前提でも defer で安全にする）
                                Flux.just(exchange.response.bufferFactory().wrap(bytes))
                            }
                    }

                val decoratedExchange = exchange.mutate().request(decoratedRequest).build()

                chain.filter(decoratedExchange)
            }.doOnCancel {
                logger.debug { "Request body $url logging cancelled" }
            }.doFinally {
                logger.info { "Request $url 完了。signal: ${it.name}" }
            }
    }
}
