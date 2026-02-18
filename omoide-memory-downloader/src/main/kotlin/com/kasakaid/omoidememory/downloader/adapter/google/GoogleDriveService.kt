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
    private val omoideMemoryTranslator: OmoideMemoryTranslator,
    private val environment: Environment,
) : DriveService {

    private val logger = KotlinLogging.logger {}

    private val driveService: Drive by lazy {
        val credentialsPath = environment.getProperty("OMOIDE_GDRIVE_CREDENTIALS_PATH")
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

    override suspend fun listFiles(): List<OmoideMemory> {
        val memories = mutableListOf<OmoideMemory>()
        var pageToken: String? = null
        
        // Fields to fetch: include imageMediaMetadata, videoMediaMetadata for OmoideMemory population
        val fields = "nextPageToken, files(id, name, mimeType, createdTime, size, imageMediaMetadata, videoMediaMetadata)"

        do {
            val result = driveService.files().list()
                .setQ("trashed = false and mimeType != 'application/vnd.google-apps.folder'")
                .setFields(fields)
                .setPageToken(pageToken)
                .execute()
            
            result.files?.forEach { file ->
                logger.debug { "Processing file: ${file.name} (${file.mimeType})" }
                omoideMemoryTranslator.translate(file).map { memory ->
                    memories.add(memory)
                }
            }
            
            pageToken = result.nextPageToken
        } while (pageToken != null)
        
        logger.info { "Found ${memories.size} files in Google Drive compatible with OmoideMemory" }
        return memories
    }

    override suspend fun downloadFile(omoideMemory: OmoideMemory): Path = withContext(Dispatchers.IO) {
        val targetPath = omoideMemory.localPath
        
        // Ensure parent directories exist
        if (targetPath.parent != null && !Files.exists(targetPath.parent)) {
            Files.createDirectories(targetPath.parent)
        }

        logger.info { "Downloading ${omoideMemory.name} to $targetPath" }
        
        Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { outputStream ->
            driveService.files().get(omoideMemory.driveFileId)
                .executeMediaAndDownloadTo(outputStream)
        }
        
        targetPath
    }
}
