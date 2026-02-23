package com.kasakaid.omoidememory.downloader.adapter

import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.service.FileIOFinish
import com.kasakaid.omoidememory.r2dbc.transaction.FatalTransactionRollback
import com.kasakaid.omoidememory.r2dbc.transaction.TransactionRollback
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name

private val logger = KotlinLogging.logger {}

object PostProcess {
    val errorLogFileName = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun onFailure(failure: DriveService.WriteError): DriveService.WriteError =
        failure.run {
            // createDirectory だとすでに存在していたら例外を投げてくる。createDirectories だと、なにもしない
            Files.createDirectories(Path.of("log"))
            val logFilePath =
                Path.of(
                    "log",
                    "failed_downloads_$errorLogFileName",
                )
            when (failure) {
                is DriveService.WriteError -> {
                    failure.paths.forEach {
                        val logEntry = "${it.toFile().name}\n"
                        try {
                            // java.io.File を使ったシンプルな追記
                            logFilePath.toFile().appendText(logEntry)
                        } catch (e: Exception) {
                            System.err.println("Failed to write to log file: ${e.message}")
                        }
                        Files.deleteIfExists(it)
                        logger.error { "バックアップ時になんらかのエラー発生。${it.name}の物理ファイルを削除します。" }
                    }
                }
            }
            failure
        }

    fun onSuccess(filePath: FileIOFinish): FileIOFinish =
        filePath.also {
            when (filePath) {
                is FileIOFinish.Skip -> {
                    logger.debug { " ${filePath.filePath} を ${filePath.reason} のためスキップ。" }
                }

                is FileIOFinish.Success -> {
                    logger.debug { "${filePath.filePath} を正常にバックアップできました。" }
                }
            }
        }

    fun onUnmanaged(transactionRollback: TransactionRollback) {
        when (transactionRollback) {
            is FatalTransactionRollback -> {
                logger.error { "予期せぬエラー ${OneLineLogFormatter.format(transactionRollback.ex)}" }
                logger.error { transactionRollback.ex }
            }

            else -> {
                logger.error { "予期せぬエラーが発生" }
            }
        }
    }
}
