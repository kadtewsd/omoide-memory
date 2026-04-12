package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
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
        private val omoideMemoryRepository: OmoideMemoryRepository,
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

                val deletedLocalIds =
                    driveService.deleteFilesByLocalIds(selectedIds) { current, total ->
                        // 🚀 進捗を通知
                        setProgress(
                            workDataOf(
                                "PROGRESS_CURRENT" to current,
                                "PROGRESS_TOTAL" to total,
                            ),
                        )
                    }

                if (deletedLocalIds.isNotEmpty()) {
                    // DB の状態を更新 (DRIVE_DELETED にする)
                    val targets = omoideMemoryRepository.findBy(deletedLocalIds)
                    omoideMemoryRepository.update(targets.map { it.driveDeleted() })
                    Log.i(TAG, "Successfully deleted ${deletedLocalIds.size} files and updated DB")
                }

                Result.success()
            }
        }
    }
