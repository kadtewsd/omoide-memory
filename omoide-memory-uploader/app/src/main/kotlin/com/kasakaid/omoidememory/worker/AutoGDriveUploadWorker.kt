package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.worker.WorkerHelper.createForegroundInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext

/**
 * 自動アップロード有効時のみアップロードを試行するクラス
 */
@HiltWorker
class AutoGDriveUploadWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
        private val gdriveUploader: GdriveUploader,
        private val localFileRepository: OmoideMemoryRepository,
    ) : CoroutineWorker(appContext, workerParams) {
        companion object {
            const val TAG = "AutoUploadWorker"
        }

        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                setForeground(appContext.createForegroundInfo("AutoUpload"))
                try {
                    Log.d(TAG, "自動アップロード開始")
                    // 1. Check Auto-Upload Setting
                    if (!omoideUploadPrefsRepository.isAutoUploadEnabled()) {
                        Log.d(TAG, "Auto-Upload が無効。写真のアップロードはスキップ")
                        return@withContext Result.success()
                    }

                    val uploadedSizes = java.util.Collections.synchronizedList(mutableListOf<Long>())
                    val uploadResult = mutableListOf<Result>()
                    var currentCount = 0

                    var shouldStop = false
                    localFileRepository
                        .getPotentialPendingFiles()
                        .takeWhile { !shouldStop }
                        .collect { omoideMemory: OmoideMemory ->
                            val currentTotalSize = uploadedSizes.sum()
                            if (currentTotalSize >= OmoideMemory.UPLOAD_LIMIT_BYTES) {
                                Log.i(TAG, "10GB の制限に達したためアップロードを中断します (現在: $currentTotalSize bytes)")
                                shouldStop = true
                                return@collect
                            }

                            Log.d(TAG, "${++currentCount}件目を開始: ${omoideMemory.name}")
                            val result =
                                gdriveUploader.upload(sourceWorker = WorkManagerTag.Auto, pendingFile = omoideMemory)

                            result.fold(
                                ifLeft = { error ->
                                    Log.e(TAG, "${omoideMemory.name} のアップロード中断: ${error.message}")
                                    uploadResult.add(Result.failure())
                                    shouldStop = true
                                },
                                ifRight = { driveId ->
                                    localFileRepository.markAsUploaded(
                                        omoideMemory.apply {
                                            driveFileId = driveId
                                            state = UploadState.DONE
                                        },
                                    )
                                    uploadedSizes.add(omoideMemory.fileSize)
                                    uploadResult.add(Result.success())
                                },
                            )
                        }
                    if (uploadResult.isNotEmpty() && uploadResult.all { it is Result.Success }) {
                        Result.success()
                    } else if (uploadResult.isEmpty()) {
                        Result.success()
                    } else {
                        Result.retry()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "例外が発生", e)
                    return@withContext Result.retry()
                }
            }
    }
