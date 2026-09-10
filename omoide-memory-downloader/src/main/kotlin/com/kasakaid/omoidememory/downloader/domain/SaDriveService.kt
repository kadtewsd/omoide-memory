package com.kasakaid.omoidememory.downloader.domain

import arrow.core.Either
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import com.kasakaid.omoidememory.infrastructure.fetchDeviceToken
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
            Drive
                .Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    HttpCredentialsAdapter(credentials),
                ).setApplicationName("OmoideMemoryDownloader")
                .build()
        }

    override suspend fun listFiles(folderId: FolderId): List<File> =
        // google-api-java-client の .execute() はブロッキング I/O のため、IO ディスパッチャーで実行する
        withContext(Dispatchers.IO) {
            val allFiles = mutableMapOf<String, File>()
            val fields = "nextPageToken, files(id, name, mimeType, createdTime, size, imageMediaMetadata, videoMediaMetadata, properties)"

            var pageToken: String? = null
            logger.info { "Processing folder: $folderId using Service Account" }
            do {
                val result =
                    driverService
                        .files()
                        .list()
                        .setQ("'$folderId' in parents and trashed = false and mimeType != 'application/vnd.google-apps.folder'")
                        .setFields(fields)
                        .setPageToken(pageToken)
                        .execute()

                result.files
                    ?.filter { file -> file.properties?.get(DOWNLOADED_PROPERTY_KEY) != "true" }
                    ?.forEach { file ->
                        if (!allFiles.containsKey(file.name)) {
                            allFiles[file.name] = file
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
        // google-api-java-client の .executeMediaAndDownloadTo() はブロッキング I/O のため、IO ディスパッチャーで実行する
        withContext(Dispatchers.IO) {
            Either.catch {
                // SA の場合は最初のサービスを使ってみる（複数の SA がある場合はどれでもアクセスできる想定、あるいは順番に試す必要があるか？）
                // ここではシンプルに最初のものを使用
                driverService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
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
        // google-api-java-client の .execute() はブロッキング I/O のため、IO ディスパッチャーで実行する
        withContext(Dispatchers.IO) {
            Either.catch {
                val metadata =
                    File().apply {
                        properties =
                            mapOf(
                                DOWNLOADED_PROPERTY_KEY to "true",
                                "downloadedAt" to
                                    java.time.Instant
                                        .now()
                                        .toString(),
                            )
                    }

                driverService
                    .files()
                    .update(fileId, metadata)
                    .setFields("properties")
                    .execute()

                Unit
            }
        }

    /**
     * 指定フォルダ内から固定ファイル名 "device_token" のファイルを検索し、その内容をテキストとして返します。
     *
     * @param accessInfo フォルダ ID（SA モードでは accessInfo = folderId）
     * @return デバイストークン文字列。ファイルが存在しない・取得失敗の場合は null
     */
    override suspend fun fetchDeviceToken(accessInfo: FolderId): String? =
        // google-api-java-client の .execute() / .executeMediaAndDownloadTo() はブロッキング I/O のため、IO ディスパッチャーで実行する
        withContext(Dispatchers.IO) {
            runCatching {
                driverService.fetchDeviceToken(
                    "'$accessInfo' in parents and name = '$DEVICE_TOKEN_FILE_NAME' and trashed = false",
                )
            }.onFailure { e ->
                logger.warn(e) { "device_token の取得に失敗しました (SA, folderId=$accessInfo)" }
            }.getOrNull()
        }
}

private const val DOWNLOADED_PROPERTY_KEY = "downloaded"

/** アップローダーとの共通規約として定義した固定ファイル名。 */
private const val DEVICE_TOKEN_FILE_NAME = "device_token"
