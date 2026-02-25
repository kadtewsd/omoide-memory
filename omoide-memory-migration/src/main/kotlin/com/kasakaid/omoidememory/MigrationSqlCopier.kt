package com.kasakaid.omoidememory

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

/**
 * JOOQ のプロジェクトから DDL を拾ってきてマイグレーションを実行する
 */
object MigrationSqlCopier {
    private val logger = KotlinLogging.logger {}

    /**
     * IDE で実行することを想定してビルド後にソースをコピーしてきます。
     * 本来はビルド前にシェルでファイルをコピーしないといけないがスキップ
     */
    fun copyDdlFromJooqProject() {
        val source = File("../omoide-memory-jooq/src/main/resources/db/migration")
        require(source.exists()) {
            "コピー元のDDLディレクトリが見つかりません: ${source.absolutePath}"
        }
        // クラスパス（ビルド済みディレクトリ）のパスを取得
        // IntelliJ で main で実行するときにクラスパスにファイルが古いものが入っているためコピーする
        val classPathUrl = this::class.java.classLoader.getResource("db/migration")
        val destinations =
            listOf(File("src/main/resources/db/migration")) +
                if (classPathUrl != null) {
                    listOf(File(classPathUrl.toURI()))
                } else {
                    // フォールバック: 最初は src にコピー（初回起動用）
                    emptyList()
                }
        destinations.forEach {
            execute(source = source, destination = it)
        }
        logger.info { "全DDLファイルのコピーが完了しました" }
    }

    private fun execute(
        source: File,
        destination: File,
    ) {
        logger.info { "コピー先ディレクトリ: $destination" }
        if (destination.exists()) {
            destination.deleteRecursively()
        }
        destination.mkdirs()
        source
            .walkTopDown()
            .filter { it.isFile && it.extension == "sql" }
            .forEach { sqlFile ->
                val destFile = destination.resolve(sqlFile.name)
                sqlFile.copyTo(destFile, overwrite = true)
                logger.info { "DDLコピー完了: ${sqlFile.name}" }
            }
    }
}
