package com.kasakaid.omoidememory.r2dbc.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Statement
import org.jooq.tools.r2dbc.LoggingConnection
import org.reactivestreams.Publisher
import org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy
import reactor.core.publisher.Mono

class R2DBCLoggingConnectionFactory(
    private val delegate: TransactionAwareConnectionFactoryProxy,
    private val mdc: Map<String, String?> = emptyMap(),
) : ConnectionFactory by delegate {
    fun withMdc(mdc: Map<String, String?>) = R2DBCLoggingConnectionFactory(delegate, mdc)

    private val logger = KotlinLogging.logger {}

    override fun create(): Publisher<out Connection> =
        Mono.from(delegate.create()).map { conn ->
            logger.debug { "cf.create: cf=${System.identityHashCode(this)} mdc=$mdc" }
            R2DBCLoggingConnection(delegate = conn, mdc = mdc)
        }
}

class R2DBCLoggingConnection(
    private val delegate: Connection,
    private val mdc: Map<String, String?>,
) : LoggingConnection(delegate) {
    private val log = KotlinLogging.logger {}

    override fun beginTransaction(): Publisher<Void> =
        Mono.from(delegate.beginTransaction()).doOnSubscribe {
            log.info { "=== [R2DBC Transaction BEGIN] ===" }
        }

    override fun commitTransaction(): Publisher<Void> =
        Mono.from(delegate.commitTransaction()).doOnSubscribe {
            log.info { "=== [R2DBC Transaction COMMIT] ===" }
        }

    override fun rollbackTransaction(): Publisher<Void> =
        Mono.from(delegate.rollbackTransaction()).doOnSubscribe {
            log.info { "=== [R2DBC Transaction ROLLBACK] ===" }
        }

    override fun createStatement(sql: String): Statement {
        val original = delegate.createStatement(sql)
        return R2DBCLoggingStatement(
            delegate = original,
            sql = sql,
            log = log,
            mdc = mdc,
        )
    }
}
