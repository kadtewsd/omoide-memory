package com.kasakaid.omoidememory.r2dbc

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KLogger
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import org.jooq.tools.r2dbc.LoggingConnection
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

class R2DBCLoggingConnectionFactory(
    private val delegate: ConnectionFactory,
) : ConnectionFactory by delegate {
    override fun create(): Publisher<out Connection> =
        Mono.from(delegate.create()).map { conn -> R2DBCLoggingConnection(conn) }
}

class R2DBCLoggingConnection(
    private val delegate: Connection,
) : LoggingConnection(delegate) {
    private val log = KotlinLogging.logger {}

    override fun createStatement(sql: String): Statement {
        val original = delegate.createStatement(sql)
        return R2DBCLoggingStatement(
            delegate = original,
            sql = sql,
            log = log,
        )
    }
}

class R2DBCLoggingStatement(
    private val delegate: Statement,
    private val sql: String,
    private val log: KLogger,
) : Statement by delegate {
    private val bindsByIndex = LinkedHashMap<Int, Any?>()

    override fun bind(index: Int, value: Any): Statement {
        bindsByIndex[index] = value
        delegate.bind(index, value)
        return this
    }

    override fun execute(): Publisher<out Result> {
        val binds = bindsByIndex
        log.info { "$sql, ${binds.entries.joinToString(prefix = "[", postfix = "]") { (k, v) -> "$k=$v" }}" }
        return delegate.execute()
    }
}
