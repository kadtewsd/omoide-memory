package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kasakaid.omoidememory.network.GoogleDriveService
import com.kasakaid.omoidememory.worker.WorkerHelper.createForegroundInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

            return withContext(Dispatchers.IO) {
                val selectedIds = inputData.getLongArray("SELECTED_IDS")?.toList()
                if (selectedIds.isNullOrEmpty()) {
                    Log.w(TAG, "No IDs to delete")
                    return@withContext Result.success()
                }

                Log.d(TAG, "Starting batch delete for ${selectedIds.size} files")

                val deleteResult =
                    driveService.deleteFilesByLocalIds(selectedIds) { current, total ->
                        // 🚀 進捗を通知
                        setProgress(
                            workDataOf(
                                "PROGRESS_CURRENT" to current,
                                "PROGRESS_TOTAL" to total,
                            ),
                        )
                    }

                Log.d(TAG, "Worker completed. deleted: ${deleteResult.deleted.size}, notDeleted: ${deleteResult.notDeleted.size}")
                val outputData =
                    workDataOf(
                        "NOT_DELETED_IDS" to deleteResult.notDeleted.toLongArray(),
                        "DELETED_IDS" to deleteResult.deleted.toLongArray(),
                    )
                Result.success(outputData)
            }
        }
    }
