package com.kasakaid.omoidememory.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.data.WifiRepository
import com.kasakaid.omoidememory.data.WifiSetting
import com.kasakaid.omoidememory.network.GoogleDriveService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Gdrive のアップロードの実装
 */
class GdriveUploader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
        private val wifiRepository: WifiRepository,
        private val omoideMemoryRepository: OmoideMemoryRepository,
        private val driveService: GoogleDriveService,
    ) {
        companion object {
            const val TAG = "GdriveUoloader"
        }

        /**
         * 写真と Video のコンテンツをアップロードする実態
         * @return アップロードされた Google Drive の File ID
         */
        suspend fun upload(
            pendingFile: OmoideMemory,
            sourceWorker: WorkManagerTag,
        ): Either<WorkerExecutionError, String> {
            val tag = "${sourceWorker.value} -> $TAG"

            val wifiSetting = wifiRepository.snapshotSsid()

            val registeredSecureSsid = omoideUploadPrefsRepository.getSecureWifiSsid()
            if (registeredSecureSsid.isNullOrEmpty()) {
                Log.w(tag, "SSID が構成されていません")
                return WorkerExecutionError.SsidNotConfigured.left()
            }

            when (wifiSetting) {
                is WifiSetting.Found -> {
                    if (wifiSetting.ssid != registeredSecureSsid) {
                        Log.w(tag, "SSID が一致しません: ${wifiSetting.ssid} != $registeredSecureSsid")
                        return WorkerExecutionError.SsidMismatch(wifiSetting.ssid, registeredSecureSsid).left()
                    }
                }

                WifiSetting.NotConnected -> {
                    return WorkerExecutionError.WifiNotConnected.left()
                }

                else -> {
                    Log.w(tag, "Wi-Fi 状態を確定できませんでした: ${wifiSetting.message}")
                    return WorkerExecutionError.WifiNotConnected.left()
                }
            }

            // 4. Upload Files
            return try {
                val fileId = driveService.uploadFile(pendingFile)
                if (fileId != null) {
                    Log.d(tag, "Uploaded: ${pendingFile.name}")
                    fileId.right()
                } else {
                    WorkerExecutionError.UploadFailed("Upload failed: fileId is null").left()
                }
            } catch (e: SecurityException) {
                Log.e(tag, "Auth Error: ${e.message}")
                WorkerExecutionError.AuthError(e.message ?: "SecurityException during upload").left()
            } catch (e: Exception) {
                Log.e(tag, "Upload Failed for ${pendingFile.name}: ${e.message}")
                WorkerExecutionError.UploadFailed(e.message ?: "Unknown error during upload").left()
            }
        }
    }
