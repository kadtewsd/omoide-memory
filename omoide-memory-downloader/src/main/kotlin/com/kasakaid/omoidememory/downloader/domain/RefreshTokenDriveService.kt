package com.kasakaid.omoidememory.downloader.domain

import arrow.core.Either
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.downloader.adapter.google.GoogleTokenCollector
import com.kasakaid.omoidememory.downloader.adapter.google.GoogleTokenCollector.executeWithSafeRefresh
import com.kasakaid.omoidememory.downloader.adapter.google.RefreshToken
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * リフレッシュトークンを使用して Google Drive にアクセスするサービス。
 *
 * 【運用上の注意】
 * Service Account（SA）を使用しない方式で運用する場合、手動でリフレッシュトークンを取得しておく必要があります。
 *
 * Google のリフレッシュトークンを取得するフローは以下の通りです：
 * 1. ユーザーがブラウザ等で認証画面を開き、認証コード（auth_code）を手動で取得します。
 * 2. 取得した auth_code を用いて、curl コマンドなどを実行し手動でリフレッシュトークンを生成・取得します。
 * 3. 取得したリフレッシュトークンをアプリケーションに設定して動作させます。
 *
 * ※ auth_code は一度しか使用できない（使用すると即座に無効化される）ため、再取得や再試行時には
 *    毎回新しく認証コードを発行する必要があります。
 */
object RefreshTokenDriveService : DriveService {
    private val logger = KotlinLogging.logger {}
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

    /**
     * ダウンロード完了後の後処理として、指定された Google Drive 上のファイルをゴミ箱に移動します。
     *
     * @param fileId 後処理対象のファイル ID
     * @param accessInfo ファイルの所有アカウントを特定するためのリフレッシュトークン
     * @return 処理結果を表す Either
     */
    override suspend fun finalize(
        fileId: String,
        accessInfo: String,
    ): Either<Throwable, Unit> =
        withContext(Dispatchers.IO) {
            Either
                .catch {
                    val drive =
                        driveServicesMap[accessInfo]
                            ?: throw IllegalArgumentException("指定された token (${accessInfo.take(8)}...) のドライブサービスが見つかりませんでした。")

                    executeWithSafeRefresh(accessInfo) {
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
                    logger.error { "ゴミ箱移動失敗: ${com.kasakaid.omoidememory.utility.OneLineLogFormatter.format(e)}" }
                    e
                }
        }
}
