package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kasakaid.omoidememory.network.GoogleDriveService
import com.kasakaid.omoidememory.os.CrashReporter
import com.kasakaid.omoidememory.worker.WorkerHelper.createForegroundInfo
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
    ) : CoroutineWorker(appContext, workerParams) {
        companion object {
            const val TAG = "ManualDeleteWorker"
        }

        override suspend fun doWork(): Result {
            // 🚀 画面ロック (通知表示)
            setForeground(appContext.createForegroundInfo("ManualDelete"))

            val selectedIds = inputData.getLongArray("SELECTED_IDS")?.toList()
            if (selectedIds.isNullOrEmpty()) {
                Log.w(TAG, "No IDs to delete")
                return Result.success()
            }

            Log.d(TAG, "Starting batch delete for ${selectedIds.size} files")

            val deleteResult =
                driveService.deleteFilesByLocalIds(
                    localIds = selectedIds,
                    onProgress = { current, total ->
                        // 🚀 進捗を通知
                        setProgress(
                            workDataOf(
                                "PROGRESS_CURRENT" to current,
                                "PROGRESS_TOTAL" to total,
                            ),
                        )
                    },
                )

            return deleteResult.fold(
                onSuccess = { res ->
                    Log.d(TAG, "Worker completed. deleted: ${res.deleted.size}, notDeleted: ${res.notDeleted.size}")
                    val outputData =
                        workDataOf(
                            "NOT_DELETED_IDS" to res.notDeleted.toLongArray(),
                            "DELETED_IDS" to res.deleted.toLongArray(),
                        )
                    Result.success(outputData)
                },
                onFailure = { error ->
                    Log.e(TAG, "Batch delete failed", error)
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
