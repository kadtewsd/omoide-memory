package com.kasakaid.pictureuploader.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kasakaid.pictureuploader.data.OmoideMemoryRepository
import com.kasakaid.pictureuploader.data.OmoideUploadPrefsRepository
import com.kasakaid.pictureuploader.data.WifiRepository
import com.kasakaid.pictureuploader.data.WifiSetting
import com.kasakaid.pictureuploader.network.GoogleDriveService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 手動アップロードを試行する
 */
@HiltWorker
class GdriveUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gdriveUploader: GdriveUploader,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "UploadWorker"
    }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "手動アップロード開始")
            gdriveUploader.upload(TAG)
        } catch (e: Exception) {
            Log.e(TAG, "Worker Exception", e)
            return@withContext Result.retry()
        }
    }
}
