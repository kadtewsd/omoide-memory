package com.kasakaid.omoidememory.downloader.domain

import arrow.core.Either
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Service Account を使用して Google Drive にアクセスするサービス
 */
class SaDriveService : DriveService {
    private val logger = KotlinLogging.logger {}

    private val driveServices: List<Drive> =
        run {
            val credentialPaths =
                System.getenv("GOOGLE_CREDENTIAL_PATHS")
                    ?: throw IllegalArgumentException("環境変数 GOOGLE_CREDENTIAL_PATHS が設定されていません。")
            credentialPaths.split(",").map { filePath ->
                val credentials =
                    ServiceAccountCredentials
                        .fromStream(FileInputStream(filePath))
                        .createScoped(listOf(DriveScopes.DRIVE)) as ServiceAccountCredentials
                Drive
                    .Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        HttpCredentialsAdapter(credentials),
                    ).setApplicationName("OmoideMemoryDownloader")
                    .build()
            }
        }

    override suspend fun listFiles(accessInfo: String): List<File> =
        withContext(Dispatchers.IO) {
            val folderId = accessInfo
            val allFiles = mutableMapOf<String, File>()
            val fields = "nextPageToken, files(id, name, mimeType, createdTime, size, imageMediaMetadata, videoMediaMetadata)"

            driveServices.forEach { drive ->
                var pageToken: String? = null
                logger.info { "Processing folder: $folderId using Service Account" }
                do {
                    val result =
                        drive
                            .files()
                            .list()
                            .setQ("'$folderId' in parents and trashed = false and mimeType != 'application/vnd.google-apps.folder'")
                            .setFields(fields)
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
            allFiles.values.toList()
        }

    override suspend fun download(
        fileId: String,
        outputStream: OutputStream,
    ): Either<Throwable, Unit> =
        withContext(Dispatchers.IO) {
            Either.catch {
                // SA の場合は最初のサービスを使ってみる（複数の SA がある場合はどれでもアクセスできる想定、あるいは順番に試す必要があるか？）
                // ここではシンプルに最初のものを使用
                val drive = driveServices.firstOrNull() ?: throw IllegalStateException("Drive service not initialized")
                drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                Unit
            }
        }
}
