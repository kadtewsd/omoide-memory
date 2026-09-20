package com.kasakaid.omoidememory.shared.spring

import com.kasakaid.omoidememory.adapter.filter.MdcFilterKey
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.context.ContextRegistry
import io.micrometer.context.ThreadLocalAccessor
import org.slf4j.MDC
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import reactor.core.publisher.Hooks

@Component
class MdcAccessorPopulate : ApplicationListener<ApplicationReadyEvent> {
    private val logger = KotlinLogging.logger {}

    /**
     * MDC が伝搬するように開くセッサーを登録します。
     */
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        // spring.reactor.context-propagation: auto しているのにそれが伝搬しない。そのため自前で auto をセット
        Hooks.enableAutomaticContextPropagation()
        logger.debug { "Automatic context propagation enabled: ${reactor.core.publisher.Hooks.isAutomaticContextPropagationEnabled()}" }

        ContextRegistry.getInstance().registerThreadLocalAccessor(Slf4jThreadLocalAccessor())

        logger.debug { "Registered accessors: ${io.micrometer.context.ContextRegistry.getInstance()}" }
    }
}

class Slf4jThreadLocalAccessor : ThreadLocalAccessor<Map<String, String>> {
    override fun key() = MdcFilterKey.MDC_DATA_KEY

    override fun getValue(): Map<String, String>? = MDC.getCopyOfContextMap()

    override fun setValue(value: Map<String, String>) = MDC.setContextMap(value)

    override fun setValue() = MDC.clear()
}
