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
                // UPLOAD_TRIGGERED のものを DB から取得
                val targets = omoideMemoryRepository.findBy(UploadState.UPLOAD_TRIGGERED)

                if (targets.isEmpty()) {
                    Log.d(TAG, "アップロード対象がありません")
                    return@withContext Result.success()
                }

                val totalCount = targets.size
                Log.d(TAG, "アップロード対象件数: $totalCount")
                var successCount = 0

                // 🚀 最初に 0 件目の進捗を出すことで、UI の「準備中」を早く終わらせる
                val successResults = mutableListOf<OmoideUploadResult.Success>()

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

                    result.fold(
                        ifLeft = { error ->
                            Log.e(TAG, "アップロード失敗: ${error.message}")
                            if (successResults.isNotEmpty()) {
                                omoideMemoryRepository.upsert(successResults.map { it.omoideMemory })
                            }
                            val pendingCount = totalCount - successResults.size
                            Log.i(TAG, "処理対象前の UPLOAD_TRIGGERED となっているレコード $pendingCount 件は何もせず次回に回します ")
                            return@withContext Result.success(
                                workDataOf(
                                    "PENDING_COUNT" to pendingCount,
                                ),
                            )
                        },
                        ifRight = { _ ->
                            successResults.add(OmoideUploadResult.Success(omoideMemory = omoideMemory))
                            successCount++
                            Log.i(TAG, "$successCount / $totalCount アップロード試行完了")
                        },
                    )
                }

                omoideMemoryRepository.upsert(successResults.map { it.omoideMemory })
                Result.success(
                    workDataOf(
                        "PENDING_COUNT" to 0,
                    ),
                )
            }
        }
    }
