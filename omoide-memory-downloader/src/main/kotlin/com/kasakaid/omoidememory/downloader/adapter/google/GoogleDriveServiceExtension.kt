package com.kasakaid.omoidememory.downloader.adapter.google

import arrow.core.Option
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.kasakaid.omoidememory.downloader.domain.DeviceToken
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Instant

private val logger = KotlinLogging.logger {}

// すでにダウンロードしたというマーク
private const val DOWNLOADED_PROPERTY_KEY = "downloaded"

/** アップローダーとの共通規約として定義した固定ファイル名。 */
private const val DEVICE_TOKEN_FILE_NAME = "device_token"

/**
 * Google Drive サービスのクライアントを生成します。
 *
 * @param credential 認証用のアダプター（[HttpRequestInitializer]）
 */
fun createDriveService(credential: HttpRequestInitializer): Drive =
    Drive
        .Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName("OmoideMemoryDownloader")
        .build()

/**
 * 指定されたクエリに合致するファイルをページングしながら全件取得します。
 * "device_token" ファイルが含まれている場合は抽出し、ダウンロードして [DeviceToken] として返します。
 * すでにダウンロード済みのプロパティが付与されているファイルは除外します。
 * 同名ファイルが存在する場合も排除せずリストに含め、ダウンロード処理側の重複チェックおよび後続の削除処理に委ねます。
 *
 * @param query Drive API の files.list クエリ文字列
 * @return デバイストークン（[Option]）と、ダウンロード対象となる Drive [File] のリストのペア
 */
fun Drive.listFiles(query: String): Pair<Option<DeviceToken>, List<File>> {
    val allFiles =
        generateSequence(fetchFilesPage(query = query, pageToken = null)) { prev ->
            prev.nextPageToken?.let { token -> fetchFilesPage(query = query, pageToken = token) }
        }.flatMap { it.files.orEmpty() }
            .filter { file -> file.properties?.get(DOWNLOADED_PROPERTY_KEY) != "true" }
            .toList()

    val (deviceTokenFiles, mediaFiles) =
        allFiles.partition { it.name == DEVICE_TOKEN_FILE_NAME }

    val deviceToken: Option<DeviceToken> =
        Option.fromNullable(
            deviceTokenFiles.firstOrNull()?.let { file ->
                runCatching {
                    val outputStream = ByteArrayOutputStream()
                    download(fileId = file.id, outputStream = outputStream)
                    outputStream.toString(Charsets.UTF_8.name()).trim()
                }.getOrNull()
            },
        )

    return deviceToken to mediaFiles
}

/**
 * 指定されたクエリとページトークンに基づいて、Google Drive から 1 ページ分のファイル一覧を取得します。
 *
 * 単一ページ（最大 1 回の API リクエスト分）のみを取得するため、全件を取得するには
 * 返却された [FileList.getNextPageToken] を用いて再帰的（または反復的）に呼び出す必要があります。
 * また、サブフォルダ配下のファイルを網羅して取得したい場合も、フォルダ階層を再帰的にトラバースして
 * 本関数を呼び出す必要があります。
 *
 * @param query Drive API の files.list クエリ文字列
 * @param pageToken 次ページ取得用のトークン。初回のページ取得時は `null`
 * @return 1 ページ分のファイル情報および次ページトークンを含む [FileList]
 */
private fun Drive.fetchFilesPage(
    query: String,
    pageToken: String?,
): FileList =
    files()
        .list()
        .setQ(query)
        .setFields("nextPageToken, files(id, name, mimeType, createdTime, size, imageMediaMetadata, videoMediaMetadata, properties)")
        .setPageToken(pageToken)
        .execute()

/**
 * 指定されたファイル ID のバイナリデータをストリームへダウンロードします。
 *
 * @param fileId ダウンロード対象のファイル ID
 * @param outputStream 書き込み先の [OutputStream]
 */
fun Drive.download(
    fileId: String,
    outputStream: OutputStream,
) {
    files().get(fileId).executeMediaAndDownloadTo(outputStream)
}

/**
 * ダウンロード完了後の後処理として、指定されたファイルに対して
 * 「ダウンロード済み」のカスタムプロパティを付与します。
 *
 * @param fileId 後処理対象のファイル ID
 */
fun Drive.markAsDownloaded(fileId: String) {
    val metadata =
        File().apply {
            properties =
                mapOf(
                    DOWNLOADED_PROPERTY_KEY to "true",
                    "downloadedAt" to Instant.now().toString(),
                )
        }

    files()
        .update(fileId, metadata)
        .setFields("properties")
        .execute()
}

/**
 */
fun Drive.moveToTrash(fileId: String) {
    files().update(fileId, File().setTrashed(true)).execute()
    logger.info { "ファイルをゴミ箱へ移動しました (ID: $fileId)" }
}
