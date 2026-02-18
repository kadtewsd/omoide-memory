package com.kasakaid.omoidememory.worker

import android.util.Log
import androidx.work.ListenableWorker
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.data.WifiRepository
import com.kasakaid.omoidememory.data.WifiSetting
import com.kasakaid.omoidememory.network.GoogleDriveService
import kotlinx.coroutines.flow.first
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

    companion object {
        const val TAG = "GdriveUoloader"
    }
    /**
     * 写真と Video のコンテンツをアップロードする実態
     */
    suspend fun upload(
        pendingFile: OmoideMemory,
        sourceWorker: WorkManagerTag,
    ): ListenableWorker.Result {
        val tag = "${sourceWorker.value} -> $TAG"
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
                Log.d(tag, "Wifi が見つかったのでアップロード開始")
                executeUpload(tag = tag, found = currentWifiSetting, pendingFile = pendingFile)
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
        found: WifiSetting.Found,
        pendingFile: OmoideMemory,
    ): ListenableWorker.Result {

        val registeredSecureSsid = omoideUploadPrefsRepository.getSecureWifiSsid()

        if (registeredSecureSsid.isNullOrEmpty()) {
            Log.w(tag, "SSID が構成されいている OK !")
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
        Log.d(tag, "Found ${pendingFile.name} pending files.")

        // 4. Upload Files
        try {
            val fileId = driveService.uploadFile(pendingFile)
            if (fileId != null) {
                omoideMemoryRepository.markAsUploaded(
                    pendingFile.onUploaded(fileId)
                )
                Log.d(tag, "Uploaded: ${pendingFile.name}")
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Auth Error: ${e.message}")
            // Trigger Re-Auth notification or UI update?
            // For now, fail so we might retry later, but better to stop if auth is broken.
            return ListenableWorker.Result.failure()
        } catch (e: Exception) {
            Log.e(tag, "Upload Failed for ${pendingFile.name}: ${e.message}")
            return ListenableWorker.Result.failure()
        }
        return ListenableWorker.Result.success().also {
            Log.i(tag, "${pendingFile.name}が正常にアップロードされました")
        }
    }
}