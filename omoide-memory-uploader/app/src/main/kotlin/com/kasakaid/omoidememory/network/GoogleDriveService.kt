package com.kasakaid.omoidememory.network

import android.content.Context
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.kasakaid.omoidememory.data.OmoideMemory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository
) {

    private val scopes = listOf(DriveScopes.DRIVE_FILE)

    suspend fun uploadFile(omoideMemory: OmoideMemory): String? = withContext(Dispatchers.IO) {
        val accountName = omoideUploadPrefsRepository.getAccountName()
        if (accountName == null) {
            throw SecurityException("No signed-in account found")
        }

        val credential = GoogleAccountCredential.usingOAuth2(context, scopes)
        credential.selectedAccountName = accountName

        val service = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("OmoideMemory").build()

        val fileMetadata = com.google.api.services.drive.model.File()
        fileMetadata.name = omoideMemory.name
        
        // Define parent folder if needed. usage: fileMetadata.parents = listOf("folderId")
        // For now, root folder.

        val mediaContent = FileContent(omoideMemory.mimeType, File(omoideMemory.filePath!!))

        try {
            val uploadedFile = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            
            uploadedFile.id
        } catch (e: Exception) {
            // Handle specific auth exceptions
            if (e.message?.contains("401") == true) {
                 // Token might be expired or revoked
                 throw SecurityException("Authentication failed: ${e.message}")
            }
            e.printStackTrace()
            null
        }
    }
}
