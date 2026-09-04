package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.worker.WorkerHelper.createForegroundInfo
import com.kasakaid.omoidememory.worker.WorkerHelper.showUploadErrorNotification
import com.kasakaid.omoidememory.worker.WorkerHelper.withWifiNetworkBinding
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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
            return appContext.withWifiNetworkBinding {
                Log.d(TAG, "UPLOAD_TRIGGERED のものを DB から取得")
                val targets = omoideMemoryRepository.findBy(UploadState.UPLOAD_TRIGGERED)

                if (targets.isEmpty()) {
                    Log.d(TAG, "アップロード対象がありません")
                    return@withWifiNetworkBinding Result.success()
                }

                val totalCount = targets.size
                Log.d(TAG, "アップロード対象件数: $totalCount")
                // 🚀 初期進捗 (0 / totalCount) を通知して UI に全体の件数を伝える
                setProgress(
                    workDataOf(
                        "PROGRESS_CURRENT" to 0,
                        "PROGRESS_TOTAL" to totalCount,
                    ),
                )
                var successCount = 0

                val successResults = mutableListOf<OmoideMemory>()

                for ((index, omoideMemory) in targets.withIndex()) {
                    setProgress(
                        workDataOf(
                            "PROGRESS_CURRENT" to index,
                            "PROGRESS_TOTAL" to totalCount,
                        ),
                    )
                    Log.d(TAG, "手動アップロード開始 ${omoideMemory.name}")
                    val result =
                        gdriveUploader.upload(
                            sourceWorker = WorkManagerTag.Manual,
                            pendingFile = omoideMemory,
                        )

                    result
                        .onSuccess { _ ->
                            successResults.add(omoideMemory.done())
                            successCount++
                            Log.i(TAG, "$successCount / $totalCount アップロード試行完了")
                        }.onFailure { error ->
                            val errorMessage = WorkerHelper.getReadableErrorMessage(error)
                            Log.e(TAG, "アップロード失敗: $errorMessage", error)
                            if (successResults.isNotEmpty()) {
                                omoideMemoryRepository.upsert(successResults)
                            }
                            val pendingCount = totalCount - successResults.size
                            Log.i(TAG, "処理対象前の UPLOAD_TRIGGERED となっているレコード $pendingCount 件は何もせず次回に回します ")
                            appContext.showUploadErrorNotification(errorMessage = errorMessage)
                            return@withWifiNetworkBinding Result.failure(
                                workDataOf(
                                    "PENDING_COUNT" to pendingCount,
                                    "ERROR_MESSAGE" to errorMessage,
                                ),
                            )
                        }
                }

                Log.d(TAG, "すべて成功になるので UPLOAD_TRIGGERED のレコードが消える")
                omoideMemoryRepository.upsert(successResults)
                Result.success(
                    workDataOf(
                        "PENDING_COUNT" to 0,
                    ),
                )
            }
        }
    }
