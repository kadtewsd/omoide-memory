package com.kasakaid.omoidememory.network

import android.content.Context
import android.util.Log
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

    private val service: Drive = run {
        val credential =
            GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
        val accountName = omoideUploadPrefsRepository.getAccountName()
        if (accountName == null) {
            throw SecurityException("No signed-in account found")
        }
        credential.selectedAccountName = accountName
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("OmoideMemory").build()
    }

    private fun OmoideMemory.toDriveFileMetaData(): com.google.api.services.drive.model.File {
        val omoide = this
        return com.google.api.services.drive.model.File().apply {
            name = omoide.name
            // サービスアカウントが利用できる ID を parents にセットする
            parents = listOf(com.kasakaid.omoidememory.BuildConfig.OMOIDE_FOLDER_ID)
        }
    }

    /**
     * ファイルをアップロードします
     */
    suspend fun uploadFile(omoideMemory: OmoideMemory): String? = withContext(Dispatchers.IO) {

        // Define parent folder if needed. usage: fileMetadata.parents = listOf("folderId")
        // For now, root folder.
        try {
            val uploadedFile = service.files().create(
                omoideMemory.toDriveFileMetaData(),
                FileContent(omoideMemory.mimeType, File(omoideMemory.filePath))
            )
                .setFields("id")
                .execute()
            uploadedFile.let {
                Log.d("GoogleDriveService", "${it.name} を ${it.parents} に ${it.id} で配置")
                it.id
            }
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
