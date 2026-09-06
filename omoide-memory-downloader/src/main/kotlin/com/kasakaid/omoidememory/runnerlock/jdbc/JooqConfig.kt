package com.kasakaid.omoidememory.runnerlock.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.yaml.snakeyaml.Yaml

/**
 * Utility object to provide a JOOQ DSLContext using a JDBC DataSource.
 *
 * The configuration is read from the application's `application.yml` where the
 * R2DBC URL, username and password are defined. The URL is transformed by
 * replacing the `r2dbc:pool:` scheme with `jdbc:` to obtain a JDBC connection
 * string compatible with PostgreSQL.
 *
 * This approach avoids using Spring's DI mechanism – the DSLContext is created
 * lazily and can be accessed via `JooqConfig.dsl()`.
 */
object JooqConfig {
    /** Lazy‑initialized JOOQ DSLContext */
    val dslContext: DSLContext =
        run {

            // Load YAML configuration
            // Load YAML configuration from classpath resource
            val input =
                Thread.currentThread().contextClassLoader.getResourceAsStream("application.yml")
                    ?: throw IllegalStateException("Configuration resource application.yml not found")

            val root = Yaml().load<Map<String, Any>>(input)

            @Suppress("UNCHECKED_CAST")
            val spring = root["spring"] as Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val r2dbc = spring["r2dbc"] as Map<String, Any>
            val rawUrl = r2dbc["url"] as String

            val config =
                HikariConfig().apply {
                    jdbcUrl = rawUrl.replaceFirst("r2dbc:pool:", "jdbc:")
                    username = r2dbc["username"] as String
                    password = r2dbc["password"] as String
                    driverClassName = "org.postgresql.Driver"
                    maximumPoolSize = 10
                    minimumIdle = 2
                    connectionTimeout = 30000
                    idleTimeout = 600000
                    maxLifetime = 1800000
                    poolName = "BatchControllApp"
                }
            DSL.using(HikariDataSource(config), SQLDialect.POSTGRES)
        }
}
