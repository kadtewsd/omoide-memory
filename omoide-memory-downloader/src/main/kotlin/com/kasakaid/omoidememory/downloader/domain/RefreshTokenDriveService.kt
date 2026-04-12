package com.kasakaid.omoidememory.downloader.domain

import arrow.core.Either
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.downloader.adapter.google.GoogleTokenCollector
import com.kasakaid.omoidememory.downloader.adapter.google.GoogleTokenCollector.executeWithSafeRefresh
import com.kasakaid.omoidememory.downloader.adapter.google.RefreshToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * リフレッシュトークンを使用して Google Drive にアクセスするサービス
 */
object RefreshTokenDriveService : DriveService {
    private val fileIdToTokenMap = ConcurrentHashMap<String, RefreshToken>()

    private val driveServicesMap: Map<RefreshToken, Drive> =
        run {
            GoogleTokenCollector.refreshTokens.associateWith { token ->
                Drive
                    .Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        GoogleTokenCollector.asHttpCredentialsAdapter(
                            GoogleTokenCollector.createUserCredentials(token),
                        ),
                    ).setApplicationName("OmoideMemoryDownloader")
                    .build()
            }
        }

    /**
     * accessInfo はこの場合アクセスするドライブのアカウントのリフレッシュトークンになります。
     */
    override suspend fun listFiles(accessInfo: RefreshToken): List<File> =
        withContext(Dispatchers.IO) {
            val drive = driveServicesMap[accessInfo] ?: throw IllegalArgumentException("指定されたトークンに対応する Drive サービスが見つかりません。")
            val allFiles = mutableMapOf<String, File>()
            val fields = "nextPageToken, files(id, name, mimeType, createdTime, size, imageMediaMetadata, videoMediaMetadata)"

            var pageToken: String? = null
            do {
                val result =
                    executeWithSafeRefresh(accessInfo) {
                        drive
                            .files()
                            .list()
                            .setQ(
                                """
                                'root' in parents
                                and trashed = false
                                and mimeType != 'application/vnd.google-apps.folder'
                                """.trimIndent(),
                            ).setFields(fields)
                            .setPageToken(pageToken)
                            .execute()
                    }

                result.files?.forEach { file ->
                    if (!allFiles.containsKey(file.name)) {
                        allFiles[file.name] = file
                        fileIdToTokenMap[file.id] = accessInfo
                    }
                }
                pageToken = result.nextPageToken
            } while (pageToken != null)
            allFiles.values.toList()
        }

    override suspend fun download(
        fileId: String,
        outputStream: OutputStream,
    ): Either<Throwable, Unit> =
        withContext(Dispatchers.IO) {
            Either.catch {
                val token =
                    fileIdToTokenMap[fileId]
                        ?: throw IllegalArgumentException("ファイル ID $fileId に対応するトークンが見つかりません。先に listFiles を実行してください。")
                val drive = driveServicesMap[token] ?: throw IllegalStateException("Drive service not initialized for token")

                executeWithSafeRefresh(token) {
                    drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                }
            }
        }
}
