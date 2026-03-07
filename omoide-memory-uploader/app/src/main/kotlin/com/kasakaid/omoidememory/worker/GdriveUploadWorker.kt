package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager.UpdateResult
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
                constructor(omoideMemory: OmoideMemory, driveId: String) : this(
                    omoideMemory = omoideMemory.done(driveId),
                )
            }

            class Fail(
                val omoideMemoryId: Long,
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
                                    OmoideUploadResult.Fail(omoideMemory.id).also {
                                        Log.e(TAG, "アップロード失敗: ${error.message}")
                                    }
                                },
                                ifRight = { driveFieldId ->
                                    OmoideUploadResult.Success(omoideMemory = omoideMemory, driveId = driveFieldId).also {
                                        successCount++
                                        Log.i(TAG, "$successCount / $totalCount アップロード試行完了")
                                    }
                                },
                            )
                    }.let { results: List<OmoideUploadResult> ->
                        omoideMemoryRepository.save(results.filterIsInstance<OmoideUploadResult.Success>().map { it.omoideMemory })
                        results.filterIsInstance<OmoideUploadResult.Fail>().let {
                            if (it.isNotEmpty()) {
                                Log.e(TAG, "${it.size} 件のアップロードに失敗しました。失敗分の Ready を解除")
                                omoideMemoryRepository.delete(it.map { it.omoideMemoryId })
                            }
                        }
                    }
                Result.success()
            }
        }
    }
