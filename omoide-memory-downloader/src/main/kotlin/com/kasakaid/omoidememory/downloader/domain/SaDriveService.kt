package com.kasakaid.omoidememory.downloader.domain

import arrow.core.Either
import arrow.core.Option
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import com.kasakaid.omoidememory.downloader.adapter.google.createDriveService
import com.kasakaid.omoidememory.downloader.adapter.google.download
import com.kasakaid.omoidememory.downloader.adapter.google.listFiles
import com.kasakaid.omoidememory.downloader.adapter.google.markAsDownloaded
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.OutputStream

typealias FolderId = String

/**
 * Service Account を使用して Google Drive にアクセスするサービス。
 *
 * ## なぜ suspend + withContext(Dispatchers.IO) を使うのか
 * `google-api-java-client` ベースの Google Drive API（`.execute()` / `executeMediaAndDownloadTo()` 等）は
 * **すべてブロッキング I/O** であり、呼び出したスレッドをネットワーク完了まで占有する。
 * コルーチンのデフォルトディスパッチャー（`Default`）はスレッド数が限られており、
 * そこでブロッキング処理を実行するとスレッド枯渇やレイテンシ増大を招く。
 * `withContext(Dispatchers.IO)` を使うことで、ブロッキング呼び出しを I/O 専用スレッドプールに
 * オフロードしつつ、呼び出し元のコルーチンはサスペンドして他の処理に譲ることができる。
 */
class SaDriveService(
    googleSaCredentialPath: String,
) : DriveService {
    private val logger = KotlinLogging.logger {}

    private val driverService: Drive =
        run {
            val credentials =
                ServiceAccountCredentials
                    .fromStream(FileInputStream(googleSaCredentialPath))
                    .createScoped(listOf(DriveScopes.DRIVE)) as ServiceAccountCredentials
            createDriveService(HttpCredentialsAdapter(credentials))
        }

    override suspend fun listFiles(folderId: FolderId): Pair<Option<DeviceToken>, List<File>> =
        withContext(Dispatchers.IO) {
            logger.info { "Processing folder: $folderId using Service Account" }
            driverService.listFiles(
                query = "'$folderId' in parents and trashed = false and mimeType != 'application/vnd.google-apps.folder'",
            )
        }

    override suspend fun download(
        fileId: String,
        outputStream: OutputStream,
    ): Either<Throwable, Unit> =
        withContext(Dispatchers.IO) {
            Either.catch {
                driverService.download(fileId = fileId, outputStream = outputStream)
            }
        }

    /**
     * ダウンロード完了後の後処理として、指定された Google Drive 上のファイルに対して
     * 「ダウンロード済み」のカスタムプロパティを付与します。
     *
     * @param fileId 後処理対象のファイル ID
     * @param accessInfo 使用しない（インターフェース互換性のため）
     * @return 処理結果を表す Either
     */
    override suspend fun finalize(
        fileId: String,
        accessInfo: String,
    ): Either<Throwable, Unit> =
        withContext(Dispatchers.IO) {
            Either.catch {
                driverService.markAsDownloaded(fileId = fileId)
            }
        }
}
