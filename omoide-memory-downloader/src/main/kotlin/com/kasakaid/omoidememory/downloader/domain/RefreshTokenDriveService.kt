package com.kasakaid.omoidememory.downloader.domain

import arrow.core.Either
import arrow.core.Option
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.downloader.adapter.google.GoogleTokenCollector
import com.kasakaid.omoidememory.downloader.adapter.google.GoogleTokenCollector.executeWithSafeRefresh
import com.kasakaid.omoidememory.downloader.adapter.google.RefreshToken
import com.kasakaid.omoidememory.downloader.adapter.google.createDriveService
import com.kasakaid.omoidememory.downloader.adapter.google.download
import com.kasakaid.omoidememory.downloader.adapter.google.listFiles
import com.kasakaid.omoidememory.downloader.adapter.google.moveToTrash
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
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
                createDriveService(
                    GoogleTokenCollector.asHttpCredentialsAdapter(
                        GoogleTokenCollector.createUserCredentials(token),
                    ),
                )
            }
        }

    /**
     * accessInfo はこの場合アクセスするドライブのアカウントのリフレッシュトークンになります。
     */
    override suspend fun listFiles(accessInfo: RefreshToken): Pair<Option<DeviceToken>, List<File>> =
        // google-api-java-client の .execute() はブロッキング I/O のため、IO ディスパッチャーで実行する
        withContext(Dispatchers.IO) {
            val drive = driveServicesMap[accessInfo] ?: throw IllegalArgumentException("指定されたトークンに対応する Drive サービスが見つかりません。")
            executeWithSafeRefresh(accessInfo) {
                drive.listFiles(
                    query =
                        """
                        'root' in parents
                        and trashed = false
                        and mimeType != 'application/vnd.google-apps.folder'
                        """.trimIndent(),
                )
            }.also { (_, files) ->
                files.forEach { file ->
                    fileIdToTokenMap[file.id] = accessInfo
                }
            }
        }

    override suspend fun download(
        fileId: String,
        outputStream: OutputStream,
    ): Either<Throwable, Unit> =
        // google-api-java-client の .executeMediaAndDownloadTo() はブロッキング I/O のため、IO ディスパッチャーで実行する
        withContext(Dispatchers.IO) {
            Either.catch {
                val token =
                    fileIdToTokenMap[fileId]
                        ?: throw IllegalArgumentException("ファイル ID $fileId に対応するトークンが見つかりません。先に listFiles を実行してください。")
                val drive = driveServicesMap[token] ?: throw IllegalStateException("Drive service not initialized for token")

                executeWithSafeRefresh(token) {
                    drive.download(fileId = fileId, outputStream = outputStream)
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
        // google-api-java-client の .execute() はブロッキング I/O のため、IO ディスパッチャーで実行する
        withContext(Dispatchers.IO) {
            Either
                .catch {
                    val drive =
                        driveServicesMap[accessInfo]
                            ?: throw IllegalArgumentException("指定された token (${accessInfo.take(8)}...) のドライブサービスが見つかりませんでした。")

                    executeWithSafeRefresh(accessInfo) {
                        drive.moveToTrash(fileId = fileId)
                    }
                }.mapLeft { e ->
                    logger.error { "ゴミ箱移動失敗: ${OneLineLogFormatter.format(e)}" }
                    e
                }
        }
}
