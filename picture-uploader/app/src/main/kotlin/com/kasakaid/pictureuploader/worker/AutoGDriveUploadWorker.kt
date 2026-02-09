package com.kasakaid.pictureuploader.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kasakaid.pictureuploader.data.OmoideUploadPrefsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自動アップロード有効時のみアップロードを試行するクラス
 */
@HiltWorker
class AutoGDriveUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
    private val gdriveUploader: GdriveUploader,
) : CoroutineWorker(appContext, workerParams) {


    companion object {
        const val TAG = "AutoUploadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "自動アップロード開始")
            // 1. Check Auto-Upload Setting
            if (!omoideUploadPrefsRepository.isAutoUploadEnabled()) {
                Log.d(TAG, "Auto-Upload が無効。写真のアップロードはスキップ")
                return@withContext Result.success()
            }
            gdriveUploader.upload(TAG)
        } catch (e: Exception) {
            Log.e(TAG, "Worker Exception", e)
            return@withContext Result.retry()
        }
    }
}