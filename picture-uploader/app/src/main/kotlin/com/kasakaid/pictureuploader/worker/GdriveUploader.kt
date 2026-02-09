package com.kasakaid.pictureuploader.worker

import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.work.ListenableWorker
import com.kasakaid.pictureuploader.data.OmoideMemoryRepository
import com.kasakaid.pictureuploader.data.OmoideUploadPrefsRepository
import com.kasakaid.pictureuploader.data.WifiRepository
import com.kasakaid.pictureuploader.data.WifiSetting
import com.kasakaid.pictureuploader.network.GoogleDriveService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Gdrive のアップロードの実装
 */
class GdriveUploader @Inject constructor(
    private val wifiRepository: WifiRepository,
    private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
    private val omoideMemoryRepository: OmoideMemoryRepository,
    private val driveService: GoogleDriveService,
) {

    /**
     * 写真と Video のコンテンツをアップロードする実態
     */
    suspend fun upload(tag: String): ListenableWorker.Result {

        // first() に条件（述語）を渡すことで、「Found になるまで（またはタイムアウトまで）待ち続ける」 という挙動に変える
        val currentWifiSetting = withTimeout(5000) { // 5 秒のタイムアウト
            wifiRepository.observeWifiSSID().first {
                it is WifiSetting.Found || it is WifiSetting.NotConnected
            }
        }
        // first の条件で else に来ることはない
        return when (currentWifiSetting) {
            WifiSetting.Idle, WifiSetting.Loading -> {
                ListenableWorker.Result.retry()
            }

            WifiSetting.NotConnected -> {
                Log.d(tag, "Wifi に接続されておらず. Retry.")
                ListenableWorker.Result.retry()
            }

            is WifiSetting.Found -> {
                executeUpload(tag, currentWifiSetting)
                ListenableWorker.Result.success()
            }
            else -> {
                Log.d(tag, "Wifi が見つからない。終了")
                ListenableWorker.Result.failure()
            }
        }
    }

    private suspend fun executeUpload(
        tag: String,
        found: WifiSetting.Found
    ): ListenableWorker.Result {

        val registeredSecureSsid = omoideUploadPrefsRepository.getSecureWifiSsid()

        if (registeredSecureSsid.isNullOrEmpty()) {
            Log.w(tag, "No target SSID configured.")
            // Should we fail or succeed?
            // Succeed to stop retrying until user Configures it.
            return ListenableWorker.Result.failure()
        }

        if (found.ssid != registeredSecureSsid) {
            Log.d(
                tag,
                "$found に接続しましたが, $registeredSecureSsid. のみアップロード可能です。スキップします"
            )
            return ListenableWorker.Result.retry()
        }

        // 3. Scan for Pending Files
        val pendingFiles = omoideMemoryRepository.getPendingFiles()
        Log.d(tag, "Found ${pendingFiles.size} pending files.")

        if (pendingFiles.isEmpty()) {
            return ListenableWorker.Result.success()
        }

        // 4. Upload Files
        var successCount = 0
        for (file in pendingFiles) {
            try {
                val fileId = driveService.uploadFile(file)
                if (fileId != null) {
                    omoideMemoryRepository.markAsUploaded(file, fileId)
                    successCount++
                    Log.d(tag, "Uploaded: ${file.name}")
                }
            } catch (e: SecurityException) {
                Log.e(tag, "Auth Error: ${e.message}")
                // Trigger Re-Auth notification or UI update?
                // For now, fail so we might retry later, but better to stop if auth is broken.
                return ListenableWorker.Result.failure()
            } catch (e: Exception) {
                Log.e(tag, "Upload Failed for ${file.name}: ${e.message}")
                // Continue to next file
            }
        }
        return ListenableWorker.Result.success().also {
            if (successCount == pendingFiles.size) {
                Log.i(tag, "すべてのファイルが正常にアップロードされました")
            } else {
                Log.w(tag, "一部のファイルアップロードが漏れています")
            }
        }
    }
}