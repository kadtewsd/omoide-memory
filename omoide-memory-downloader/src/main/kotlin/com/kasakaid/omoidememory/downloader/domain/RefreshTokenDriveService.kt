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
import com.google.auth.oauth2.ServiceAccountCredentials
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
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.Ref
import kotlin.io.path.name

val driveServiceSa: Drive by lazy {
    Drive
        .Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(GoogleTokenCollector.createServiceAccountCredentials()),
        ).setApplicationName("OmoideMemoryDownloader")
        .build()
}

/**
 * Google のドライブにアクセスするためのサービス
 */
class GoogleDriveService(
    private val locationService: LocationService,
    private val folderIds: List<String>,
) : DriveService {
    private val logger = KotlinLogging.logger {}

    /**
     * GDrive は名前が重複していても別ファイルとして扱える。
     * やりたいこととしてはそこまで厳密に違うものとして扱いたいわけではなく、
     * 同じファイル名であれば同じものとして扱うで十分、そのため、重複は落とす。
     */
    override suspend fun listFiles(): Map<RefreshToken, List<File>> {
        val resultFilesMap = mutableMapOf<RefreshToken, List<File>>()
        // SA を使って指定された全てのフォルダからファイルを収集
        val allFiles = mutableMapOf<String, File>()
        val fields =
            "nextPageToken, files(id, name, mimeType, createdTime, size, imageMediaMetadata, videoMediaMetadata)"

        this.folderIds.forEach { folderId ->
            var pageToken: String? = null
            logger.info { "Processing folder: $folderId using Service Account" }
            do {
                val result =
                    driveServiceSa
                        .files()
                        .list()
                        .setQ(
                            """
                            '$folderId' in parents
                            and trashed = false
                            and mimeType != 'application/vnd.google-apps.folder'
                            """.trimIndent(),
                        ).setFields(fields)
                        .setPageToken(pageToken)
                        .execute()

                result.files?.forEach { file ->
                    if (!allFiles.containsKey(file.name)) {
                        allFiles[file.name] = file
                    }
                }
                pageToken = result.nextPageToken
            } while (pageToken != null)
        }

        // ダウンローダーの既存ロジックが refreshToken 毎のループを想定しているため、
        // 便宜上、最初のリフレッシュトークンに全てのファイルを紐づける
        // (削除は SA では行わず、このリフレッシュトークンを使って後で行う想定)
        val firstToken = GoogleTokenCollector.refreshTokens.firstOrNull()
        if (firstToken != null) {
            resultFilesMap[firstToken] = allFiles.values.toList()
        }

        logger.info { "Found ${allFiles.size} unique files in Google Drive across ${folderIds.size} folders." }
        return resultFilesMap
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
        token: String,
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
                            // ダウンロードは SA で行う
                            driveServiceSa
                                .files()
                                .get(googleFile.id)
                                .executeMediaAndDownloadTo(outputStream)
                            Unit.right()
                        }
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
}
