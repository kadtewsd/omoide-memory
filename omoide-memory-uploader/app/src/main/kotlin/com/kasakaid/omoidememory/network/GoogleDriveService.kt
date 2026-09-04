package com.kasakaid.omoidememory.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
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
        companion object {
            /**
             * 接続タイムアウト (ミリ秒): 60秒
             * モバイル端末の不安定なネットワーク環境を考慮し、デフォルトの20秒から延長
             */
            private const val CONNECT_TIMEOUT_MS = 60_000

            /**
             * 読み取りタイムアウト (ミリ秒): 180秒 (3分)
             * 写真や大容量動画のアップロード中に SocketTimeoutException が発生するのを防ぐため、
             * デフォルトの20秒から大幅に延長
             */
            private const val READ_TIMEOUT_MS = 180_000

            /**
             * アップロード完了後のインターバル待機時間 (ミリ秒)
             * 連続アップロードによるレートリミット (429) の発生を抑制するためのクールダウン
             */
            private const val UPLOAD_INTERVAL_DELAY_MS = 800L

            /**
             * ファイル削除後のインターバル待機時間 (ミリ秒)
             * 連続削除リクエストによるレートリミット (429) の発生を抑制するためのクールダウン
             */
            private const val DELETE_INTERVAL_DELAY_MS = 800L

            /**
             * Resumable Upload のチャンクサイズ (バイト): 2MB
             * 低速な Wi-Fi 上り回線でも 1 チャンクがタイムアウトせず確実に送れるサイズに設定
             * (MediaHttpUploader.MINIMUM_CHUNK_SIZE = 256KB の倍数)
             */
            private const val UPLOAD_CHUNK_SIZE_BYTES = MediaHttpUploader.MINIMUM_CHUNK_SIZE * 8
        }

        private val accountName: String =
            omoideUploadPrefsRepository.getAccountName() ?: throw SecurityException("共有したいフォルダを持つアカウントでログインしてください")
        private val service: Drive =
            run {
                val credentials =
                    GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE)).apply {
                        selectedAccountName = accountName
                    }

                Drive
                    .Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                    ) { request ->
                        credentials.initialize(request)
                        request.connectTimeout = CONNECT_TIMEOUT_MS
                        request.readTimeout = READ_TIMEOUT_MS
                    }.setApplicationName("OmoideMemory")
                    .build()
            }

        /**
         * ファイルをアップロードします (ブロッキング同期実行)
         */
        fun uploadFile(omoideMemory: OmoideMemory): Result<String> =
            runCatching {
                val createRequest =
                    service
                        .files()
                        .create(
                            metadataProvider.createMetadata(omoideMemory),
                            FileContent(omoideMemory.mimeType, File(omoideMemory.filePath!!)),
                        ).setFields("id")

                createRequest.mediaHttpUploader?.setChunkSize(UPLOAD_CHUNK_SIZE_BYTES)

                val uploadedFile = createRequest.execute()

                val fileId = uploadedFile?.id ?: throw IOException("Upload failed: ID is null for ${omoideMemory.name}")
                Thread.sleep(UPLOAD_INTERVAL_DELAY_MS)
                fileId
            }

        private class DeleteCandidate(
            val lid: Long,
            val fileId: String,
            val isDownloaded: Boolean,
        )

        /**
         * 端末側の ID に基づいて Google Drive 上のファイルを削除します。
         *
         * @return 削除結果 (削除されたIDリストと削除されなかったIDリスト)
         */
        suspend fun deleteFilesByLocalIds(
            localIds: List<Long>,
            onProgress: suspend (Int, Int) -> Unit,
        ): Result<DeleteResult> {
            val driveFiles = mutableListOf<Pair<Long, String>>() // localId to driveFileId
            val allFoundLocalIds = mutableSetOf<Long>()
            val notDeletedLocalIds = mutableListOf<Long>()
            val deletedLocalIds = mutableListOf<Long>()
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
                        notDeletedLocalIds.add(candidate.lid)
                        Log.i(
                            "Drive",
                            "File ${candidate.lid} (Drive ID: ${candidate.fileId}) is not yet marked as downloaded; skipping deletion",
                        )
                    }
                }

                // ローカルの中でサーバー上に見つからなかったものは「削除成功扱い」
                val notFoundLocalIds = localIds.toSet() - allFoundLocalIds
                deletedLocalIds.addAll(notFoundLocalIds)

                if (driveFiles.isEmpty()) {
                    onProgress(0, 0)
                    return Result.success(
                        DeleteResult(
                            deleted = deletedLocalIds,
                            notDeleted = notDeletedLocalIds,
                        ),
                    )
                }

                // 2. 見つかったファイルを順次削除 (429 対策でインターバルを設ける)
                driveFiles.forEachIndexed { index, (lid, driveId) ->
                    onProgress(index, driveFiles.size)
                    try {
                        service.files().delete(driveId).execute()
                        deletedLocalIds.add(lid)
                        Log.i("Drive", "Deleted file from Drive: $driveId (localId: $lid)")
                        // 🚀 429 対策: 削除の間に少し待機
                        delay(DELETE_INTERVAL_DELAY_MS)
                    } catch (e: Exception) {
                        if (e is GoogleJsonResponseException && e.statusCode == 404) {
                            Log.i("Drive", "File not found on Drive during delete, treating as deleted: $driveId (localId: $lid)")
                            deletedLocalIds.add(lid)
                        } else {
                            Log.e("Drive", "Failed to delete file from Drive: $driveId (localId: $lid)", e)
                            val remainingLocalIds = driveFiles.subList(index, driveFiles.size).map { it.first }
                            notDeletedLocalIds.addAll(remainingLocalIds)
                            return Result.failure(e)
                        }
                    }
                }
                onProgress(driveFiles.size, driveFiles.size)
                return Result.success(
                    DeleteResult(
                        deleted = deletedLocalIds,
                        notDeleted = notDeletedLocalIds,
                    ),
                )
            } catch (e: Exception) {
                Log.e("Drive", "Failed in batch delete process", e)
                return Result.failure(e)
            }
        }

        /**
         * 端末側の ID に基づいて Google Drive 上のファイルを削除します。
         */
        suspend fun deleteFileByLocalId(localId: Long): Boolean {
            val result =
                deleteFilesByLocalIds(
                    localIds = listOf(localId),
                    onProgress = { _, _ -> },
                )
            return result.fold(
                onSuccess = { deleteResult -> deleteResult.notDeleted.isEmpty() },
                onFailure = { false },
            )
        }
    }

data class DeleteResult(
    val deleted: List<Long>,
    val notDeleted: List<Long>,
)
