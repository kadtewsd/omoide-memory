package com.kasakaid.omoidememory.downloader.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.domain.LocationService
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import com.kasakaid.omoidememory.domain.SourceFile
import com.kasakaid.omoidememory.downloader.adapter.google.*
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.domain.OmoideMemoryExportService
import com.kasakaid.omoidememory.downloader.domain.SaDriveService
import com.kasakaid.omoidememory.downloader.service.DownloadFileBackUpService
import com.kasakaid.omoidememory.r2dbc.transaction.RollbackException
import com.kasakaid.omoidememory.utility.CoroutineHelper.mapWithCoroutine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.sync.Semaphore
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "download-from-gdrive")
class DownloadFromGDrive(
    private val locationService: LocationService,
    private val syncedMemoryRepository: OmoideMemoryRepository,
    private val transactionalOperator: TransactionalOperator,
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

            val saPaths = System.getenv("GOOGLE_SA_CREDENTIAL_PATHS")
            val (accessInfos, driveService) =
                if (!saPaths.isNullOrBlank()) {
                    logger.info { "Service Account モードで起動します。" }
                    val folderIdsString = System.getenv("OMOIDE_FOLDER_ID") ?: ""
                    val folderIds = folderIdsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (folderIds.isEmpty()) {
                        throw IllegalArgumentException("対象のフォルダIDを OMOIDE_FOLDER_ID 環境変数に指定してください（カンマ区切り可）。")
                    }
                    folderIds to SaDriveService()
                } else {
                    logger.info { "Refresh Token モードで起動します。" }
                    GoogleTokenCollector.refreshTokens to RefreshTokenDriveService(folderIds = folderIds)
                }

            val omoideMemoryExportService =
                OmoideMemoryExportService(
                    locationService = locationService,
                )
            val downloadFileBackUpService =
                DownloadFileBackUpService(
                    syncedMemoryRepository = syncedMemoryRepository,
                    omoideMemoryExportService = omoideMemoryExportService,
                )

            logger.info { "Google Drive からのダウンロード処理を開始します (対象ドライブ数: ${accessInfos.size})" }

            accessInfos.forEach { accessInfo ->
                logger.info { "[$accessInfo] のファイルをスキャン中..." }

                // 1. Google Driveから対象のアクセス情報に基づいてファイルを取得
                val googleFiles = driveService.listFiles(accessInfo)
                logger.info { "[$accessInfo] で ${googleFiles.size} 件のファイルが見つかりました。" }

                // Google API のレートに引っ掛かるなどの可能性があるので 10 程度にする
                googleFiles.mapWithCoroutine(Semaphore(10)) { googleFile ->
                    try {
                        transactionalOperator.executeAndAwait {
                            kotlinx.coroutines.withContext(MDCContext(mapOf("requestId" to "${googleFile.name}:${googleFile.id}"))) {
                                // 1. Download to temp file
                                val tempPath = Files.createTempFile("omoide_", "_${googleFile.name}")
                                logger.debug { "Gdrive からのダウンロード開始 ->  $tempPath" }
                                Files
                                    .newOutputStream(tempPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                                    .use { outputStream ->
                                        driveService.download(googleFile.id, outputStream).onLeft {
                                            throw it
                                        }
                                    }

                                downloadFileBackUpService
                                    .execute(
                                        tempPath = tempPath,
                                        fileName = googleFile.name,
                                        sourceFile = SourceFile.fromGoogleDrive(googleFile),
                                        omoideBackupPath = Path.of(destination),
                                        familyId = familyId,
                                    ).onRight {
                                        GoogleDriveToTrash.finalize(googleFile.id, accessInfo)
                                        PostProcess.onSuccess(it)
                                    }.onLeft {
                                        Files.deleteIfExists(tempPath)
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
                    } catch (e: Throwable) {
                        logger.error(e) { "予期せぬエラーが発生しました: ${googleFile.name}" }
                    }
                }
            }
            PostProcess.finish()
            logger.info { "Google Drive からのダウンロード処理をすべて終了。" }
        }
}
