package com.kasakaid.omoidememory.downloader.adapter

import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.service.FileIOFinish
import com.kasakaid.omoidememory.r2dbc.transaction.RollbackException
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name

private val logger = KotlinLogging.logger {}

object PostProcess {
    private val errorLogFileName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"))
    private val failedPaths = java.util.Collections.synchronizedList(mutableListOf<Path>())
    private var successCount = 0
    private var failureCount = 0

    fun onFailure(failure: DriveService.WriteError): DriveService.WriteError =
        failure.run {
            when (failure) {
                is DriveService.WriteError -> {
                    failure.paths.forEach {
                        failedPaths.add(it)
                        failureCount++
                        logger.error { "バックアップ時になんらかのエラー発生。${it.name}の物理ファイルを削除します。" }
                        Files.deleteIfExists(it)
                    }
                }
            }
            failure
        }

    /**
     * 全ダウンロード処理の終了後に呼び出します。
     * 失敗ファイルのログ書き出しを行い、device_token が渡された場合は PUSH 通知を送信します。
     *
     * @param deviceToken FCM デバイストークン。null の場合は PUSH 通知をスキップします。
     */
    fun finish(deviceToken: String?) {
        if (failedPaths.isNotEmpty()) {
            Files.createDirectories(Path.of("log"))
            val logFilePath =
                Path.of(
                    "log",
                    "failed_downloads_$errorLogFileName",
                )
            val logContent = failedPaths.joinToString("\n") { it.toFile().name } + "\n"
            try {
                logFilePath.toFile().writeText(logContent)
            } catch (e: Exception) {
                System.err.println("Failed to write to log file: ${e.message}")
            }
        }

        if (deviceToken != null) {
            PushNotificationService.send(
                deviceToken = deviceToken,
                successCount = successCount,
                failureCount = failureCount,
            )
        } else {
            logger.info { "device_token が見つからなかったため PUSH 通知をスキップします" }
        }
    }

    fun onSuccess(filePath: FileIOFinish): FileIOFinish =
        filePath.also {
            when (filePath) {
                is FileIOFinish.Skip -> {
                    logger.debug { " ${filePath.filePath} を ${filePath.reason} のためスキップ。" }
                }

                is FileIOFinish.Success -> {
                    successCount++
                    logger.debug { "${filePath.filePath} を正常にバックアップできました。" }
                }
            }
        }

    fun onUnmanaged(transactionRollback: RollbackException) {
        failureCount++
        logger.error { "予期せぬエラー ${OneLineLogFormatter.format(transactionRollback)}" }
        logger.error { transactionRollback.leftValue }
    }
}
