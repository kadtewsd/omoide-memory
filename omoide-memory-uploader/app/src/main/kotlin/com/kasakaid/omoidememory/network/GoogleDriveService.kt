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
        private val metadataProvider: DriveMetadataProvider,
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
                                metadataProvider.createMetadata(omoideMemory),
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
                        return@withContext uploadFile(
                            omoideMemory = omoideMemory,
                            attempt =
                                attempt + 1,
                        )
                    } else {
                        Log.e("Drive", "All attempts failed for ${omoideMemory.name}", e)
                        return@withContext null
                    }
                }
            }

        private class DeleteCandidate(
            val lid: Long,
            val fileId: String,
            val isDownloaded: Boolean,
        )

        /**
         * 端末側の ID に基づいて Google Drive 上のファイルを削除します。
         */
        suspend fun deleteFilesByLocalIds(
            localIds: List<Long>,
            onProgress: suspend (Int, Int) -> Unit,
        ): List<Long> =
            withContext(Dispatchers.IO) {
                val driveFiles = mutableListOf<Pair<Long, String>>() // localId to driveFileId
                val allFoundLocalIds = mutableSetOf<Long>()
                try {
                    // 1. 対象のファイルをまとめて探す (N+1 解消)
                    // 30件ずつバッチ処理してクエリ長制限を回避
                    localIds.chunked(30).forEach { batch ->
                        val idQueries = batch.joinToString(" or ") { "appProperties has { key='local_id' and value='$it' }" }
                        val query =
                            "appProperties has { key='origin_device_id' and value='${Build.ID}' } " +
                                "and trashed = false and ($idQueries)"

                        val fileList =
                            service
                                .files()
                                .list()
                                .setQ(query)
                                .setSpaces("drive")
                                .setFields("files(id, appProperties, properties)")
                                .execute()

                        if (fileList.files == null) return@forEach
                        val (downloaded, not) =
                            fileList.files
                                .asSequence()
                                .mapNotNull { file ->
                                    val lid = file.appProperties?.get("local_id")?.toLongOrNull() ?: return@mapNotNull null
                                    DeleteCandidate(
                                        lid = lid,
                                        isDownloaded = file.properties?.get("downloaded") == "true",
                                        fileId = file.id,
                                    )
                                }.partition { it.isDownloaded }

                        downloaded.forEach { candidate ->
                            allFoundLocalIds.add(candidate.lid)
                            driveFiles.add(candidate.lid to candidate.fileId)
                        }

                        not.forEach { candidate ->
                            allFoundLocalIds.add(candidate.lid)
                            Log.i(
                                "Drive",
                                "File ${candidate.lid} (Drive ID: ${candidate.fileId}) is not yet marked as downloaded; skipping deletion",
                            )
                        }
                    }

                    // ローカルの中でサーバー上に見つからなかったものは「削除済み扱い」
                    val deletedLocalIds = (localIds.toSet() - allFoundLocalIds).toMutableList()

                    if (driveFiles.isEmpty()) {
                        onProgress(0, 0)
                        return@withContext deletedLocalIds
                    }

                    // 2. 見つかったファイルを順次削除 (429 対策で delay を入れる)
                    driveFiles.forEachIndexed { index, (lid, driveId) ->
                        onProgress(index, driveFiles.size)
                        try {
                            service.files().delete(driveId).execute()
                            deletedLocalIds.add(lid)
                            Log.i("Drive", "Deleted file from Drive: $driveId (localId: $lid)")
                            // 🚀 429 対策: 削除の間に少し待機
                            delay(500)
                        } catch (e: Exception) {
                            if (e is GoogleJsonResponseException && e.statusCode == 404) {
                                Log.i("Drive", "File not found on Drive during delete, treating as deleted: $driveId (localId: $lid)")
                                deletedLocalIds.add(lid)
                            } else {
                                Log.e("Drive", "Failed to delete file from Drive: $driveId (localId: $lid)", e)
                            }
                        }
                    }
                    onProgress(driveFiles.size, driveFiles.size)
                    return@withContext deletedLocalIds
                } catch (e: Exception) {
                    Log.e("Drive", "Failed in batch delete process", e)
                    return@withContext emptyList<Long>()
                }
            }

        /**
         * 端末側の ID に基づいて Google Drive 上のファイルを削除します。
         */
        suspend fun deleteFileByLocalId(localId: Long): Boolean = deleteFilesByLocalIds(listOf(localId)) { _, _ -> }.isNotEmpty()
    }
