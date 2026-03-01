package com.kasakaid.omoidememory.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
    ) {
        private val accountName: String = omoideUploadPrefsRepository.getAccountName() ?: throw SecurityException("共有したいフォルダを持つアカウントでログインしてください")
        private val service: Drive =
            run {
                val credentials =
                    GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE)).apply {
                        selectedAccountName = accountName
                    }
                // 2. Google API Client との橋渡しに HttpCredentialsAdapter を使用
                Drive
                    .Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        credentials,
                    ).setApplicationName("OmoideMemory")
                    .build()
            }

        private fun OmoideMemory.toDriveFileMetaData(): com.google.api.services.drive.model.File {
            val omoide = this
            return com.google.api.services.drive.model.File().apply {
                // 1. 基本情報
                name = omoide.name
                mimeType = omoide.mimeType

                // 3. 撮影日時をセット (Drive上での「作成日」として扱われる)
                // omoide.dateTaken はミリ秒(Long)を想定
                createdTime = omoide.dateTaken?.let { DateTime(it) }

                // 4. 説明文に端末情報を入れる (後でドライブ内検索が可能)
                description = "Uploaded by OmoideMemory App\n" +
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "Original Path: ${omoide.filePath}"

                // 5. アプリ専用の隠しプロパティ (ユーザーには見えないがAPIから取得可能)
                // 重複排除やDBとの紐付けに非常に有益
                appProperties =
                    mapOf(
                        "local_id" to omoide.id.toString(),
                        "origin_device_id" to Build.ID, // 端末識別の一助
                    )
            }
        }

        /**
         * ファイルをアップロードします
         */
        suspend fun uploadFile(
            omoideMemory: OmoideMemory,
            attempt: Int = 1,
        ): String? =
            withContext(Dispatchers.IO) {
                val maxAttempts = 3
                try {
                    // 1. ファイル本体のアップロード
                    val uploadedFile =
                        service
                            .files()
                            .create(
                                omoideMemory.toDriveFileMetaData(),
                                FileContent(omoideMemory.mimeType, File(omoideMemory.filePath)),
                            ).setFields("id")
                            .execute()

                    if (uploadedFile?.id == null) throw IOException("Upload failed: ID is null")
                    // 全工程成功。次のファイル処理のために少し待機
                    delay(800)
                    return@withContext uploadedFile.id
                } catch (e: Exception) {
                    val isRateLimit = e is GoogleJsonResponseException && e.statusCode == 429

                    if (attempt < maxAttempts) {
                        val waitTime = if (isRateLimit) 5000L * attempt else 2000L * attempt
                        Log.w("Drive", "Attempt $attempt failed. Retrying in ${waitTime}ms... Error: ${e.message}")

                        delay(waitTime)
                        return@withContext uploadFile(omoideMemory, attempt + 1)
                    } else {
                        Log.e("Drive", "All attempts failed for ${omoideMemory.name}", e)
                        return@withContext null
                    }
                }
            }
    }
