package com.kasakaid.omoidememory.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.Constraints
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
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
        ): String {
            val tag = "${sourceWorker.value} -> $TAG"
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)

            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                throw RuntimeException("WiFi is not connected")
            }
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
            // first 的な条件で else に来ることはない
            val registeredSecureSsid = omoideUploadPrefsRepository.getSecureWifiSsid()

            if (registeredSecureSsid.isNullOrEmpty()) {
                Log.w(tag, "SSID が構成されていません")
                throw RuntimeException("SSID is not configured")
            }

            // 4. Upload Files
            try {
                val fileId = driveService.uploadFile(pendingFile)
                if (fileId != null) {
                    Log.d(tag, "Uploaded: ${pendingFile.name}")
                    return fileId
                } else {
                    throw RuntimeException("Upload failed: fileId is null")
                }
            } catch (e: SecurityException) {
                Log.e(tag, "Auth Error: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Upload Failed for ${pendingFile.name}: ${e.message}")
                throw e
            }
        }
    }
