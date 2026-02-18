package com.kasakaid.omoidememory

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

/**
 * JOOQ のプロジェクトから DDL を拾ってきてマイグレーションを実行する
 */
object MigrationSqlCopier {

    private val logger = KotlinLogging.logger {}

    fun copyDdlFromJooqProject() {
        val source = File("../omoide-memory-jooq/src/main/resources/db/migration")
        val destination = File("src/main/resources/db/migration")

        require(source.exists()) {
            "コピー元のDDLディレクトリが見つかりません: ${source.absolutePath}"
        }

        if (destination.exists()) {
            destination.deleteRecursively()
        }
        destination.mkdirs()

        source.walkTopDown()
            .filter { it.isFile && it.extension == "sql" }
            .forEach { sqlFile ->
                val destFile = destination.resolve(sqlFile.name)
                sqlFile.copyTo(destFile, overwrite = true)
                logger.info { "DDLコピー完了: ${sqlFile.name}" }
            }

        logger.info { "全DDLファイルのコピーが完了しました" }
    }
}