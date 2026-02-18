package com.kasakaid.omoidememory.downloader.adapter.google

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.downloader.domain.DriveService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Service
class GoogleDriveService(
    private val omoideMemoryTranslator: OmoideMemoryTranslator,
): DriveService {

    private val logger = KotlinLogging.logger {}

    private val driveService: Drive by lazy {
        val credentialsPath = System.getenv("OMOIDE_GDRIVE_CREDENTIALS_PATH")
            ?: throw IllegalArgumentException("OMOIDE_GDRIVE_CREDENTIALS_PATH env var is not set")
        
        val credentials = FileInputStream(credentialsPath).use {
            GoogleCredential.fromStream(it)
                .createScoped(listOf(DriveScopes.DRIVE))
        }

        Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credentials
        ).setApplicationName("OmoideMemoryDownloader").build()
    }

    override suspend fun listFiles(): List<OmoideMemory> = withContext(Dispatchers.IO) {
        val files = mutableListOf<OmoideMemory>()
        var pageToken: String? = null
        
        do {
            val result = driveService.files().list()
                .setQ("trashed = false and mimeType != 'application/vnd.google-apps.folder'")
                .setFields("nextPageToken, files(id, name, mimeType, createdTime)")
                .setPageToken(pageToken)
                .execute()
            
            result.files?.map { file ->
                omoideMemoryTranslator.translate(file)
            }?.let { files.addAll(it) }
            
            pageToken = result.nextPageToken
        } while (pageToken != null)
        
        logger.info { "Found ${files.size} files in Google Drive" }
        files
    }

    suspend fun downloadFile(driveFile: DriveFile): OmoideMemory = withContext(Dispatchers.IO) {
        val tmpDir = Path.of(System.getProperty("java.io.tmpdir"))
        val targetPath = tmpDir.resolve(driveFile.name)
        
        logger.info { "Downloading ${driveFile.name} to $targetPath" }
        
        Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { outputStream ->
            driveService.files().get(driveFile.id)
                .executeMediaAndDownloadTo(outputStream)
        }
        
        OmoideMemory(
            localPath = targetPath,
            name = driveFile.name,
            mimeType = driveFile.mimeType,
            driveFileId = driveFile.id
        )
    }

    suspend fun deleteFile(fileId: String) = withContext(Dispatchers.IO) {
        logger.info { "Deleting file from Google Drive: $fileId" }
        driveService.files().delete(fileId).execute()
    }
}

