package com.kasakaid.omoidememory.downloader.adapter

import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.service.DownloadFileBackUpService
import com.kasakaid.omoidememory.r2dbc.transaction.TransactionAttemptFailure
import com.kasakaid.omoidememory.r2dbc.transaction.TransactionExecutor
import com.kasakaid.omoidememory.utility.CoroutineHelper.mapWithCoroutine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.appendText
import kotlin.io.path.name

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "download-from-gdrive")
class DownloadFromGDrive(
    private val driveService: DriveService,
    private val downloadFileBackUpService: DownloadFileBackUpService,
    private val transactionExecutor: TransactionExecutor,
    private val environment: Environment,
) : ApplicationRunner {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    override fun run(args: ApplicationArguments): Unit =
        runBlocking {
            logger.info { "Google Driveからのダウンロード処理を開始します" }
            // 1. Google Driveから対象フォルダ配下の全ファイルを取得
            val googleDriveFilesInfo: List<File> = driveService.listFiles()
            // Google API のレートに引っ掛かるなどの可能性があるので 10 程度にする
            googleDriveFilesInfo.mapWithCoroutine(Semaphore(10)) { googleFile ->
                transactionExecutor
                    .executeWithPerLineLeftRollback(
                        "${googleFile.name}:${googleFile.id}",
                    ) {
                        downloadFileBackUpService
                            .execute(
                                googleFile = googleFile,
                                omoideBackupPath = Path.of(environment.getProperty("OMOIDE_BACKUP_DESTINATION")!!),
                            ).onLeft {
                                val logFilePath =
                                    Path.of(
                                        System.getProperty("user.dir"),
                                        "failed_downloads_${
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                                        }",
                                    )
                                it.paths.forEach {
                                    val logEntry = "${googleFile.name}\n"
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
                    }.fold(
                        ifLeft = {
                            when (it) {
                                is TransactionAttemptFailure.Unmanaged -> {
                                    logger.error { it.ex }
                                }
                            }
                        },
                        ifRight = {
                            "${googleFile.id} の ${googleFile.name}".let { skippedFileName ->
                                when (it) {
                                    is DownloadFileBackUpService.FileIOFinish.Skip -> {
                                        logger.debug { " $skippedFileName をスキップしました。${it.reason}" }
                                    }

                                    DownloadFileBackUpService.FileIOFinish.Success -> {
                                        logger.debug { "$skippedFileName を正常にバックアップできました。" }
                                    }
                                }
                            }
                        },
                    )
            }
            logger.info { "Google Driveからのダウンロード処理を終了。" }
        }
}
