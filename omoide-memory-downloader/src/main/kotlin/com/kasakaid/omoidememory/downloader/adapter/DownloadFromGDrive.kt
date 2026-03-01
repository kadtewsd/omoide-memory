package com.kasakaid.omoidememory.downloader.adapter

import arrow.core.left
import arrow.core.right
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.downloader.adapter.google.GoogleDriveService
import com.kasakaid.omoidememory.downloader.adapter.google.GoogleTokenCollector
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.service.DownloadFileBackUpService
import com.kasakaid.omoidememory.r2dbc.transaction.RollbackException
import com.kasakaid.omoidememory.utility.CoroutineHelper.mapWithCoroutine
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
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
) : ApplicationRunner {
    override fun run(args: ApplicationArguments): Unit =
        runBlocking {
            val familyId = System.getenv("OMOIDE_FAMILY_ID")
            if (familyId.isNullOrBlank()) {
                throw IllegalArgumentException(
                    "ファミリーIDを OMOIDE_FAMILY_ID 環境変数に指定してください。",
                )
            }

            val destination =
                System.getenv("OMOIDE_BACKUP_DIRECTORY")
                    ?: throw IllegalArgumentException(
                        "OMOIDE_BACKUP_DIRECTORY 環境変数が設定されていません。",
                    )

            if (!Path.of(destination).isAbsolute) {
                throw IllegalArgumentException("絶対パスを指定してください。")
            }

            logger.info { "Google Drive からのダウンロード処理を開始します (対象アカウント数: ${GoogleTokenCollector.refreshTokens.size})" }

            // 1. Google Driveから対象フォルダ配下の全ファイルを取得 (全アカウントから)
            val accountsFilesMap = driveService.listFiles()
            logger.info { "合計 ${accountsFilesMap.values.sumOf { it.size }} 件のファイルが見つかりました。" }

            accountsFilesMap.forEach { (refreshToken, googleDriveFilesInfo) ->
                logger.info { "アカウント (${refreshToken.take(8)}...) の ${googleDriveFilesInfo.size} 件のファイルを処理します" }

                // Google API のレートに引っ掛かるなどの可能性があるので 10 程度にする
                googleDriveFilesInfo.mapWithCoroutine(Semaphore(10)) { googleFile ->
                    try {
                        transactionalOperator.executeAndAwait {
                            kotlinx.coroutines.withContext(MDCContext(mapOf("requestId" to "${googleFile.name}:${googleFile.id}"))) {
                                downloadFileBackUpService
                                    .execute(
                                        googleFile = googleFile,
                                        omoideBackupPath = Path.of(destination),
                                        familyId = familyId,
                                        refreshToken = refreshToken,
                                    ).onRight {
                                        PostProcess.onSuccess(it)
                                    }.onLeft {
                                        throw RollbackException(it)
                                    }
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
            }
            PostProcess.finish()
            logger.info { "Google Drive からのダウンロード処理をすべて終了。" }
        }
}
