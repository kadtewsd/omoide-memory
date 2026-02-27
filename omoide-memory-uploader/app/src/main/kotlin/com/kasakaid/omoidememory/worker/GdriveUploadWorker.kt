package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.worker.WorkerHelper.createForegroundInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 手動アップロードを試行する
 */
@HiltWorker
class GdriveUploadWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val gdriveUploader: GdriveUploader,
        private val omoideMemoryRepository: OmoideMemoryRepository,
    ) : CoroutineWorker(appContext, workerParams) {
        companion object {
            const val TAG = "ManualUploadWorker"
        }

        override suspend fun doWork(): Result {
            setForeground(appContext.createForegroundInfo("ManualUpload"))
            return withContext(Dispatchers.IO) {
                // READY のものを DB から取得
                val targets = omoideMemoryRepository.findReadyForUpload()

                if (targets.isEmpty()) {
                    Log.d(TAG, "アップロード対象がありません")
                    return@withContext Result.success()
                }

                val totalCount = targets.size
                Log.d(TAG, "アップロード対象件数: $totalCount")
                var successCount = 0

                try {
                    for (file in targets) {
                        Log.d(TAG, "手動アップロード開始 ${file.name}")
                        val driveId =
                            gdriveUploader.upload(
                                sourceWorker = WorkManagerTag.Manual,
                                pendingFile = file,
                            )

                        omoideMemoryRepository.markAsDone(
                            id = file.id,
                            driveFileId = driveId,
                        )

                        successCount++
                        Log.i(TAG, "$successCount / $totalCount アップロード試行完了")

                        setProgress(
                            workDataOf(
                                "PROGRESS_CURRENT" to successCount,
                                "PROGRESS_TOTAL" to totalCount,
                            ),
                        )
                    }
                    Result.success()
                } catch (e: Exception) {
                    Log.e(TAG, "例外が発生", e)
                    Result.retry()
                }
            }
        }
    }
