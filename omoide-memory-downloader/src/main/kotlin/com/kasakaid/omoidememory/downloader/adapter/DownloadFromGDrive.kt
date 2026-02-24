package com.kasakaid.omoidememory.downloader.adapter

import arrow.core.left
import arrow.core.right
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.service.DownloadFileBackUpService
import com.kasakaid.omoidememory.r2dbc.transaction.RollbackException
import com.kasakaid.omoidememory.utility.CoroutineHelper.mapWithCoroutine
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.executeAndAwait
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "download-from-gdrive")
class DownloadFromGDrive(
    private val driveService: DriveService,
    private val downloadFileBackUpService: DownloadFileBackUpService,
    private val transactionalOperator: org.springframework.transaction.reactive.TransactionalOperator,
    private val environment: Environment,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments): Unit =
        runBlocking {
            logger.info { "Google Driveからのダウンロード処理を開始します" }
            // 1. Google Driveから対象フォルダ配下の全ファイルを取得
            val googleDriveFilesInfo: List<File> = driveService.listFiles()
            // Google API のレートに引っ掛かるなどの可能性があるので 10 程度にする
            googleDriveFilesInfo.mapWithCoroutine(Semaphore(10)) { googleFile ->
                try {
                    transactionalOperator.executeAndAwait {
                        downloadFileBackUpService
                            .execute(
                                googleFile = googleFile,
                                omoideBackupPath = Path.of(environment.getProperty("OMOIDE_BACKUP_DESTINATION")!!),
                            ).onRight {
                                PostProcess.onSuccess(it)
                            }.onLeft {
                                throw RollbackException(it)
                            }
                    }
                } catch (e: RollbackException) {
                    val error = e.leftValue
                    when (error) {
                        is DriveService.WriteError -> PostProcess.onFailure(error)
                        else -> PostProcess.onUnmanaged(e)
                    }
                }
            }
            logger.info { "Google Driveからのダウンロード処理を終了。" }
        }
}
