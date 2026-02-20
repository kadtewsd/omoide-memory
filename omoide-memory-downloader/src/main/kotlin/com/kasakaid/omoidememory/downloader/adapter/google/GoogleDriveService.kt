package com.kasakaid.omoidememory.downloader.adapter.google

import arrow.core.Either
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.kasakaid.omoidememory.domain.*
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.domain.MediaType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Google のドライブにアクセスするためのサービス
 */
@Service
class GoogleDriveService(
    private val environment: Environment,
) : DriveService {

    private val logger = KotlinLogging.logger {}

    private val driveService: Drive = run {
        environment.getProperty("OMOIDE_GDRIVE_CREDENTIALS_PATH").let { pathFromHome ->
            if (pathFromHome.isNullOrBlank()) {
                throw IllegalArgumentException("OMOIDE_GDRIVE_CREDENTIALS_PATH がセットされていない")
            }

            val credentialsPath = Path.of(System.getProperty("user.home"))
                .resolve(pathFromHome)
                .normalize()

            val credentials = FileInputStream(credentialsPath.toFile()).use {
                GoogleCredentials.fromStream(it)
                    .createScoped(listOf(DriveScopes.DRIVE))
            }

            val requestInitializer = HttpCredentialsAdapter(credentials)

            Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer
            ).setApplicationName("OmoideMemoryDownloader").build()
        }
    }

    override suspend fun listFiles(): List<File> {
        val googleFiles = mutableListOf<File>()
        var pageToken: String? = null

        // Fields to fetch: include imageMediaMetadata, videoMediaMetadata for OmoideMemory population
        val fields =
            "nextPageToken, files(id, name, mimeType, createdTime, size, imageMediaMetadata, videoMediaMetadata)"

        do {
            val result = driveService.files().list()
                .setQ(
                    """
                    trashed = false 
                    and mimeType != 'application/vnd.google-apps.folder'
                    and '${environment.getProperty("OMOIDE_FOLDER_ID")}' in parents
                    """.trimIndent()
                )
                .setFields(fields)
                .setPageToken(pageToken)
                .execute()

            result.files?.forEach { file ->
                logger.debug { "Processing file: ${file.name} (${file.mimeType})" }
                googleFiles.add(file)
            }

            pageToken = result.nextPageToken
        } while (pageToken != null)

        logger.info { "Found ${googleFiles.size} files in Google Drive compatible with OmoideMemory" }
        return googleFiles
    }

    override suspend fun writeOmoideMemoryToTargetPath(
        googleFile: File,
        omoideBackupPath: Path,
        mediaType: MediaType,
    ): Either<MetadataExtractError, OmoideMemory> = withContext(Dispatchers.IO) {
        // 1. OS標準の一時ディレクトリにファイルを確保
        // prefixとsuffixを指定するだけで、Windowsなら AppData\Local\Temp、Macなら /var/folders 等に作られます
        val tempPath = Files.createTempFile("omoide_", "_${googleFile.name}")
        // 2026年現在のベストプラクティス:
        // OutputStream 自体も .use で確実に閉じ、I/Oスレッドで実行する
        Files.newOutputStream(tempPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            .use { outputStream ->
                driveService.files().get(googleFile.id)
                    .executeMediaAndDownloadTo(outputStream)
                // executeMediaAndDownloadTo は内部でループして書き込むため、
                // ここに到達した時点でディスクへの書き出しは完了しています
            }
        val metadata = mediaType.createMediaMetadata(tempPath)
        val finalTargetPath = FileOrganizeService.determineTargetPath(
            fileName = googleFile.name,
            captureTime = metadata.capturedTime,
        )
        FileOrganizeService.moveToTarget(sourcePath = tempPath, targetPath = finalTargetPath)
        Files.deleteIfExists(tempPath)
        // 5. ファイル実体からメタデータを抽出（ここで captureTime が判明）
        metadata.toMedia(SourceFile.fromGoogleDrive(googleFile))
    }
}
