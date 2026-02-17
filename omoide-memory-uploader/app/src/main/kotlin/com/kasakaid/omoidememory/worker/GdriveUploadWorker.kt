package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 手動アップロードを試行する
 */
@HiltWorker
class GdriveUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gdriveUploader: GdriveUploader,
    private val omoideMemoryRepository: OmoideMemoryRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "UploadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 引数からハッシュリストを取得
        val targetHashes = inputData.getStringArray("TARGET_HASHES")?.toList() ?: emptyList()
        val totalCount = inputData.getInt("TOTAL_COUNT", 0)
        var successCount = 0
        try {
            // ここでは Flow (川) は不要。ViewModel 側の川はそのままになり、ここでは都度どんととってきてしまう。last で全部のデータが取ってこられたあとのものをガツっと取得
            omoideMemoryRepository.getActualPendingFiles().collect { file ->
                if (file.hash in targetHashes) {
                    Log.d(TAG, "手動アップロード開始")
                    gdriveUploader.upload(tag = TAG, pendingFile = file).also {
                        successCount++
                        Log.i(TAG, "$successCount / $totalCount アップロード完了")
                    }
                    setProgress(
                        workDataOf(
                            "PROGRESS_CURRENT" to successCount,
                            "PROGRESS_TOTAL" to totalCount,
                        )
                    )
                }
            }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "例外が発生", e)
            return@withContext Result.retry()
        }
    }

}
