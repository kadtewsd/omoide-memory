package com.kasakaid.omoidememory.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FlywayConfig {

    @Bean
    fun flywayMigrationStrategy(): FlywayMigrationStrategy {
        return MyFlywayMigration
    }
}

private val logger = KotlinLogging.logger {}

/**
 * マイグレーションの実装側
 */
object MyFlywayMigration : FlywayMigrationStrategy {
    override fun migrate(flyway: Flyway) {
        logger.info { "Flyway マイグレーションを開始します..." }

        val pending = flyway.info().pending()

        logger.info { "${pending.size} 件のマイグレーションが見つかりました。順次実行します。" }

        pending.iterator().forEachRemaining { migrationInfo ->
            logger.info { "マイグレーション適用中: ${migrationInfo.script} (バージョン: ${migrationInfo.version})" }

            val stepFlyway = Flyway.configure()
                .configuration(flyway.configuration)
                .target(migrationInfo.version)
                .initSql("SET lock_timeout = '5s';")
                .load()

            try {
                val result = stepFlyway.migrate()
                if (result.success) {
                    logger.info { "マイグレーション成功: ${migrationInfo.script}" }
                } else {
                    logger.warn { "マイグレーションが完了しましたが、何か問題がある可能性があります: ${migrationInfo.script}" }
                }
            } catch (e: Exception) {
                logger.error {
                    "マイグレーション中にエラーが発生しました (スクリプト: ${migrationInfo.script}): ${e.message}"
                }
                logger.error { e.stackTrace }
                throw e
            }
        }

        logger.info { "すべてのマイグレーションが完了しました。" }
    }
}