package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kasakaid.omoidememory.network.GoogleDriveService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 手動削除を試行する
 */
@HiltWorker
class GdriveDeleteWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val googleDriveService: GoogleDriveService,
    ) : CoroutineWorker(appContext, workerParams) {
        companion object {
            const val TAG = "ManualDeleteWorker"
        }

        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val targetHashes = inputData.getStringArray("TARGET_HASHES")?.toList() ?: emptyList()
                val totalCount = inputData.getInt("TOTAL_COUNT", 0)
                Log.d(TAG, "受け取った削除ハッシュ件数: ${targetHashes.size}, 合計件数: $totalCount")
                var successCount = 0
                try {
                    for (hash in targetHashes) {
                        Log.d(TAG, "手動削除開始 hash: $hash")
                        val success = googleDriveService.deleteFileByHash(hash)
                        if (success) {
                            successCount++
                            Log.i(TAG, "$successCount / $totalCount 削除試行完了")
                        }
                        setProgress(
                            workDataOf(
                                "PROGRESS_CURRENT" to successCount,
                                "PROGRESS_TOTAL" to totalCount,
                            ),
                        )
                    }
                    return@withContext Result.success()
                } catch (e: Exception) {
                    Log.e(TAG, "例外が発生", e)
                    return@withContext Result.retry()
                }
            }
    }
