package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.network.GoogleDriveService
import com.kasakaid.omoidememory.os.CrashReporter
import com.kasakaid.omoidememory.worker.WorkerHelper.createForegroundInfo
import com.kasakaid.omoidememory.worker.WorkerHelper.showDeleteCompleteNotification
import com.kasakaid.omoidememory.worker.WorkerHelper.showDeleteErrorNotification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Google Drive からのファイル物理削除を実行する Worker
 */
@HiltWorker
class GdriveDeleteWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val driveService: GoogleDriveService,
        private val omoideMemoryRepository: OmoideMemoryRepository,
    ) : CoroutineWorker(appContext, workerParams) {
        companion object {
            const val TAG = "ManualDeleteWorker"
        }

        override suspend fun doWork(): Result {
            // 🚀 画面ロック (通知表示)
            setForeground(appContext.createForegroundInfo("ManualDelete"))

            val targets = omoideMemoryRepository.findBy(state = UploadState.DELETE_TRIGGERED)
            if (targets.isEmpty()) {
                Log.w(TAG, "No IDs to delete")
                return Result.success()
            }

            Log.d(TAG, "Starting batch delete for ${targets.size} files")

            val targetIds = targets.map { it.id }
            val deleteResult =
                driveService.deleteFilesByLocalIds(
                    localIds = targetIds,
                    onProgress = { current, total ->
                        // 🚀 進捗を通知
                        setProgress(
                            workDataOf(
                                "PROGRESS_CURRENT" to current,
                                "PROGRESS_TOTAL" to total,
                            ),
                        )
                    },
                    onDeleted = { localId ->
                        // 1件削除成功するたびに即座に DRIVE_DELETED に更新し、途中で 429 等が発生しても成功分を保持する
                        omoideMemoryRepository.updateState(ids = setOf(localId), state = UploadState.DRIVE_DELETED)
                    },
                )

            return deleteResult.fold(
                onSuccess = { res ->
                    Log.d(TAG, "Worker completed. deleted: ${res.deleted.size}, notDeleted: ${res.notDeleted.size}")
                    // ダウンロード前などでスキップされたファイルは元の DONE に戻す
                    if (res.notDeleted.isNotEmpty()) {
                        omoideMemoryRepository.updateState(ids = res.notDeleted, state = UploadState.DONE)
                    }
                    driveService
                        .deleteDeviceToken()
                        .onFailure { e -> Log.w(TAG, "device_token の削除に失敗しました (無視して継続)", e) }
                    appContext.showDeleteCompleteNotification(
                        deletedCount = res.deleted.size,
                        notDeletedCount = res.notDeleted.size,
                    )
                    val outputData =
                        workDataOf(
                            "NOT_DELETED_COUNT" to res.notDeleted.size,
                            "DELETED_COUNT" to res.deleted.size,
                        )
                    Result.success(outputData)
                },
                onFailure = { error ->
                    Log.e(TAG, "Batch delete failed (429 or other error)", error)
                    // 429等のエラー発生時: 待機やリトライはせず即座に中断し、
                    // 未削除のまま残ったファイル（DELETE_TRIGGERED のままのもの）をすべて DONE に戻して再選択可能にする
                    val remaining = omoideMemoryRepository.findBy(state = UploadState.DELETE_TRIGGERED)
                    if (remaining.isNotEmpty()) {
                        Log.i(TAG, "未削除の ${remaining.size} 件を DONE に復元します")
                        omoideMemoryRepository.updateState(remaining.map { it.id }.toSet(), UploadState.DONE)
                    }
                    val errorMessage = WorkerHelper.getReadableErrorMessage(error)
                    appContext.showDeleteErrorNotification(errorMessage)
                    CrashReporter.saveReport(
                        context = appContext,
                        action = "DELETE",
                        throwable = error,
                    )
                    Result.failure()
                },
            )
        }
    }
