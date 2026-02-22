package com.kasakaid.omoidememory.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.kasakaid.omoidememory.BuildConfig
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
    ) {
        private val service: Drive =
            run {
                val credentials =
                    context.assets.open(BuildConfig.SA_KEY_FILE_NAME).use {
                        GoogleCredentials.fromStream(it)
                        // アカウント情報だと SA を使って、Drive からダウンロード済みのファイルを削除できない。
                        // 自分の持ち物であったら削除できるので、SA アカウントを使ってアップロードする
                        // GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
                    }
                val accountName = omoideUploadPrefsRepository.getAccountName()
                if (accountName == null) {
                    throw SecurityException("No signed-in account found")
                }
                // 2. Google API Client との橋渡しに HttpCredentialsAdapter を使用
                val requestInitializer = HttpCredentialsAdapter(credentials)
                Drive
                    .Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        requestInitializer,
                    ).setApplicationName("OmoideMemory")
                    .build()
            }

        private fun OmoideMemory.toDriveFileMetaData(): com.google.api.services.drive.model.File {
            val omoide = this
            return com.google.api.services.drive.model.File().apply {
                // 1. 基本情報
                name = omoide.name
                mimeType = omoide.mimeType

                // 2. 保存先（ビルド時に指定したフォルダID）
                parents = listOf(com.kasakaid.omoidememory.BuildConfig.OMOIDE_FOLDER_ID)

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
                        "file_hash" to omoide.hash,
                        "local_id" to omoide.id,
                        "origin_device_id" to Build.ID, // 端末識別の一助
                    )
            }
        }

        /**
         * ファイルをアップロードします
         */
        suspend fun uploadFile(omoideMemory: OmoideMemory): String? =
            withContext(Dispatchers.IO) {
                // Define parent folder if needed. usage: fileMetadata.parents = listOf("folderId")
                // For now, root folder.
                try {
                    val uploadedFile =
                        service
                            .files()
                            .create(
                                omoideMemory.toDriveFileMetaData(),
                                FileContent(omoideMemory.mimeType, File(omoideMemory.filePath)),
                            ).setFields("id")
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
