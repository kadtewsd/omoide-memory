package com.kasakaid.omoidememory.r2dbc

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions.*
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.time.Duration

@Configuration
class R2DBCConfiguration(
    private val environment: Environment,
) {

    @Bean
    fun connectionFactory(): ConnectionFactory {
        val url = environment.getProperty("spring.r2dbc.url")
            ?: throw IllegalArgumentException("spring.r2dbc.url を application.yml にセットすること")
        val username = environment.getProperty("spring.r2dbc.username")
            ?: throw IllegalArgumentException("spring.r2dbc.username を application.yml にセットすること")
        val password = environment.getProperty("spring.r2dbc.password")
            ?: throw IllegalArgumentException("spring.r2dbc.password を application.yml にセットすること")

        // r2dbc:postgresql://localhost:5432/omoide_memory?currentSchema=omoide_memory
        // からホスト・ポート・データベース名を抽出
        val regex = """r2dbc:pool:postgresql://([^:]+):(\d+)/([^?]+)""".toRegex()
        val matchResult = regex.find(url) ?: throw IllegalArgumentException("Invalid R2DBC URL format")
        val (host, port, database) = matchResult.destructured

        return ConnectionFactories.get(
            builder()
                .option(DRIVER, "postgresql")
                .option(HOST, host)
                .option(PORT, port.toInt())
                .option(DATABASE, database)
                .option(USER, username)
                .option(PASSWORD, password)
                .option(CONNECT_TIMEOUT, Duration.ofSeconds(20))
                .build()
        ).let { R2DBCLoggingConnectionFactory(it) }
    }

    @Bean
    fun dslContext(connectionFactory: ConnectionFactory): DSLContext {
        return DSL.using(
            connectionFactory,
            SQLDialect.POSTGRES,
            DefaultConfiguration().apply {
                setSQLDialect(SQLDialect.POSTGRES)
            }.settings()
        )
    }
}
