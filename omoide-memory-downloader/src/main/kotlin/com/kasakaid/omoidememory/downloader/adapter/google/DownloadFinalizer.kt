package com.kasakaid.omoidememory.downloader.adapter.google

import arrow.core.Either
import arrow.core.right
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.downloader.adapter.google.GoogleTokenCollector.executeWithSafeRefresh
import com.kasakaid.omoidememory.downloader.service.DownloadFinalizer
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 何もしない後処理
 */
object NoOpes : DownloadFinalizer {
    override suspend fun finalize(
        fileId: String,
        token: RefreshToken,
    ): Either<Throwable, Unit> = Unit.right()
}

/**
 * Google Drive のファイルをゴミ箱に移動するための後処理
 */
object GoogleDriveToTrash : DownloadFinalizer {
    private val logger = KotlinLogging.logger {}

    private val driveServices: Map<RefreshToken, Drive> by lazy {
        GoogleTokenCollector.refreshTokens.associateWith { token ->
            val creds = GoogleTokenCollector.createUserCredentials(token)
            Drive
                .Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    GoogleTokenCollector.asHttpCredentialsAdapter(creds),
                ).setApplicationName("OmoideMemoryDownloader")
                .build()
        }
    }

    override suspend fun finalize(
        fileId: String,
        token: RefreshToken,
    ): Either<Throwable, Unit> =
        withContext(Dispatchers.IO) {
            Either
                .catch {
                    val drive =
                        driveServices[token]
                            ?: throw IllegalArgumentException("指定された token (${token.take(8)}...) のドライブサービスが見つかりませんでした。")

                    executeWithSafeRefresh(token) {
                        val fileInfo =
                            drive
                                .files()
                                .get(fileId)
                                .setFields("name, parents")
                                .execute()
                        val fileName = fileInfo.name
                        val parents = fileInfo.parents

                        if (fileName != null && parents != null && parents.isNotEmpty()) {
                            val parentId = parents[0]
                            val q = "name = '${fileName.replace("'", "\\'")}' and '$parentId' in parents and trashed = false"
                            val filesToDelete =
                                drive
                                    .files()
                                    .list()
                                    .setQ(q)
                                    .setFields("files(id)")
                                    .execute()
                            filesToDelete.files?.forEach { f ->
                                drive.files().update(f.id, File().setTrashed(true)).execute()
                                logger.info { "同名ファイルをゴミ箱へ移動しました: $fileName (ID: ${f.id})" }
                            }
                        } else {
                            drive.files().update(fileId, File().setTrashed(true)).execute()
                        }
                    }
                    Unit
                }.mapLeft { e ->
                    logger.error { "ゴミ箱移動失敗: ${OneLineLogFormatter.format(e)}" }
                    e
                }
        }
}
