package com.kasakaid.omoidememory.downloader.adapter.google

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials
import com.google.gson.JsonParser
import com.kasakaid.omoidememory.domain.*
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.domain.MediaType
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.name

/**
 * Google のドライブにアクセスするためのサービス
 */
@Component
class GoogleDriveService(
    private val locationService: LocationService,
) : DriveService {
    private val logger = KotlinLogging.logger {}

    private val driveService: Drive get() =
        Drive
            .Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                GoogleTokenCollector.asHttpCredentialsAdapter(),
            ).setApplicationName("OmoideMemoryDownloader")
            .build()

    /**
     * リフレッシュトークンを使ってアクセストークンを新しく生成します。
     */
    private suspend fun <T> executeWithSafeRefresh(block: suspend () -> T): T =
        try {
            block()
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode == 401) {
                logger.warn { "401 Unauthorized detected. Refreshing token manually and retrying..." }
                GoogleTokenCollector.refreshIfNeeded()
                block()
            } else {
                throw e
            }
        }

    override suspend fun listFiles(gdriveFolderId: String): List<File> {
        val googleFiles = mutableListOf<File>()
        var pageToken: String? = null

        // Fields to fetch: include imageMediaMetadata, videoMediaMetadata for OmoideMemory population
        val fields =
            "nextPageToken, files(id, name, mimeType, createdTime, size, imageMediaMetadata, videoMediaMetadata)"

        do {
            val result =
                executeWithSafeRefresh {
                    driveService
                        .files()
                        .list()
                        .setQ(
                            """
                            trashed = false
                            and mimeType != 'application/vnd.google-apps.folder'
                            and '$gdriveFolderId' in parents
                            """.trimIndent(),
                        ).setFields(fields)
                        .setPageToken(pageToken)
                        .execute()
                }

            result.files?.forEach { file ->
                logger.debug { "Processing file: ${file.name} (${file.mimeType})" }
                googleFiles.add(file)
            }

            pageToken = result.nextPageToken
        } while (pageToken != null)

        logger.info { "Found ${googleFiles.size} files in Google Drive compatible with OmoideMemory" }
        return googleFiles
    }

    suspend fun <T> tryIo(
        path: Path,
        block: suspend () -> Either<*, T>,
    ): Either<DriveService.WriteError, T> =
        tryIo(setOf(path)) {
            block()
        }

    /**
     * Metaデータを ImageMetadataReader で読み込むときに失敗するかもしれない対策
     */
    suspend fun <T> tryIo(
        paths: Set<Path>,
        block: suspend () -> Either<*, T>,
    ): Either<DriveService.WriteError, T> =
        try {
            block().mapLeft {
                DriveService.WriteError(paths)
            }
        } catch (e: Exception) {
            logger.error { "書き込みでエラーが発生: ${OneLineLogFormatter.format(e)}" }
            logger.error { e }
            DriveService.WriteError(paths).left()
        }

    override suspend fun writeOmoideMemoryToTargetPath(
        googleFile: File,
        omoideBackupPath: Path,
        mediaType: MediaType,
        familyId: String,
    ): Either<DriveService.WriteError, OmoideMemory> =
        withContext(Dispatchers.IO) {
            either {
                // 1. OS標準の一時ディレクトリにファイルを確保
                // prefixとsuffixを指定するだけで、Windowsなら AppData\Local\Temp、Macなら /var/folders 等に作られます
                val tempPath = Files.createTempFile("omoide_", "_${googleFile.name}")
                // 2026年現在のベストプラクティス:
                // OutputStream 自体も .use で確実に閉じ、I/Oスレッドで実行する
                logger.debug { "Gdrive からのダウンロード開始 ->  $tempPath" }
                tryIo(path = tempPath) {
                    Files
                        .newOutputStream(tempPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                        .use { outputStream ->
                            executeWithSafeRefresh {
                                driveService
                                    .files()
                                    .get(googleFile.id)
                                    .executeMediaAndDownloadTo(outputStream)
                            }
                            // executeMediaAndDownloadTo は内部でループして書き込むため、
                            // ここに到達した時点でディスクへの書き出しは完了しています
                        }.right()
                }.bind()

                logger.debug { "${googleFile.name}をローカル ${tempPath.name} にダウンロードしてメタデータを抽出" }

                val metadata: MediaMetadata =
                    tryIo(tempPath) {
                        mediaType.createMediaMetadata(LocalFile(path = tempPath, name = googleFile.name)).right()
                    }.bind()

                logger.debug { "captureTime が判明。${metadata.capturedTime} ${googleFile.name}のファイルパスを決める" }

                val finalTargetPath =
                    tryIo(tempPath) {
                        FileOrganizeService
                            .determineTargetPath(
                                fileName = googleFile.name,
                                captureTime = metadata.capturedTime,
                                omoideBackupDirectory = omoideBackupPath,
                            ).right()
                    }.bind()

                logger.debug { "${googleFile.name}のファイルパスが $finalTargetPath となったので、$tempPath を削除" }
                tryIo(finalTargetPath) {
                    FileOrganizeService.moveToTarget(sourcePath = tempPath, targetPath = finalTargetPath).right()
                }.bind()

                tryIo(setOf(tempPath, finalTargetPath)) {
                    Files.deleteIfExists(tempPath).right()
                }.bind()
                logger.debug { "一時ファイル ${googleFile.name} を削除完了" }
                // 正しいパスでメタデータを生成。少し勿体無いが確実に正しいパスで新規にインスタンスを生成
                tryIo(setOf(tempPath, finalTargetPath)) {
                    val finalMetadata = mediaType.createMediaMetadata(LocalFile(path = finalTargetPath, name = finalTargetPath.fileName.toString()))
                    val locationName =
                        if (finalMetadata is PhotoMetadata) {
                            finalMetadata.gpsDirectory?.geoLocation?.let { geo ->
                                if (geo.isZero) {
                                    null
                                } else {
                                    locationService.getLocationName(geo.latitude, geo.longitude)
                                }
                            }
                        } else {
                            null
                        }

                    finalMetadata
                        .toMedia(
                            sourceFile = SourceFile.fromGoogleDrive(googleFile),
                            familyId = familyId,
                            locationName = locationName,
                        ).mapLeft {
                            logger.error { "一時ファイル ${googleFile.name} のメディア化失敗。" }
                            logger.error { OneLineLogFormatter.format(it.ex) }
                            logger.error { it.ex }
                            DriveService.WriteError(finalTargetPath)
                        }.onRight {
                            logger.debug { "${it.captureTime} で ${it.localPath.name} のエンティティ化が成功" }
                        }
                }.bind()
            }
        }

    override suspend fun moveToTrash(fileId: String): Either<Throwable, Unit> =
        withContext(Dispatchers.IO) {
            Either
                .catch {
                    executeWithSafeRefresh {
                        driveService.files().update(fileId, File().setTrashed(true)).execute()
                    }
                    Unit
                }.mapLeft { e ->
                    logger.error { "ゴミ箱移動失敗: ${OneLineLogFormatter.format(e)}" }
                    e
                }
        }
}
