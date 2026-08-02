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

        sealed interface OmoideUploadResult {
            class Success private constructor(
                val omoideMemory: OmoideMemory,
            ) : OmoideUploadResult {
                companion object {
                    operator fun invoke(omoideMemory: OmoideMemory) = Success(omoideMemory.done())
                }
            }

            class Fail(
                val omoideMemoryId: Long,
                val error: WorkerExecutionError,
            ) : OmoideUploadResult
        }

        override suspend fun doWork(): Result {
            setForeground(appContext.createForegroundInfo("ManualUpload"))
            return withContext(Dispatchers.IO) {
                // READY のものを DB から取得
                val targets = omoideMemoryRepository.findBy(UploadState.READY)

                if (targets.isEmpty()) {
                    Log.d(TAG, "アップロード対象がありません")
                    return@withContext Result.success()
                }

                val totalCount = targets.size
                Log.d(TAG, "アップロード対象件数: $totalCount")
                var successCount = 0

                // 🚀 最初に 0 件目の進捗を出すことで、UI の「準備中」を早く終わらせる
                val results =
                    targets
                        .mapIndexed { index, omoideMemory ->
                            setProgress(
                                workDataOf(
                                    "PROGRESS_CURRENT" to index,
                                    "PROGRESS_TOTAL" to totalCount,
                                ),
                            )
                            Log.d(TAG, "手動アップロード開始 ${omoideMemory.name}")
                            gdriveUploader
                                .upload(
                                    sourceWorker = WorkManagerTag.Manual,
                                    pendingFile = omoideMemory,
                                ).fold(
                                    ifLeft = { error ->
                                        OmoideUploadResult.Fail(omoideMemory.id, error).also {
                                            Log.e(TAG, "アップロード失敗: ${error.message}")
                                        }
                                    },
                                    ifRight = { _ ->
                                        OmoideUploadResult.Success(omoideMemory = omoideMemory).also {
                                            successCount++
                                            Log.i(TAG, "$successCount / $totalCount アップロード試行完了")
                                        }
                                    },
                                )
                        }

                omoideMemoryRepository.update(results.filterIsInstance<OmoideUploadResult.Success>().map { it.omoideMemory })
                val failedResults = results.filterIsInstance<OmoideUploadResult.Fail>()
                if (failedResults.isNotEmpty()) {
                    Log.e(TAG, "${failedResults.size} 件のアップロードに失敗しました。失敗分を UPLOAD_PENDING に更新")
                    val failedEntities = omoideMemoryRepository.findBy(failedResults.map { it.omoideMemoryId })
                    omoideMemoryRepository.update(failedEntities.map { it.triggered() })
                }

                val storageFullCount = failedResults.count { it.error is WorkerExecutionError.StorageFull }
                val authErrorCount = failedResults.count { it.error is WorkerExecutionError.AuthError }
                val otherErrorCount = failedResults.size - storageFullCount - authErrorCount

                Result.success(
                    workDataOf(
                        "PENDING_COUNT" to failedResults.size,
                        "STORAGE_FULL_COUNT" to storageFullCount,
                        "AUTH_ERROR_COUNT" to authErrorCount,
                        "OTHER_ERROR_COUNT" to otherErrorCount,
                    ),
                )
            }
        }
    }
