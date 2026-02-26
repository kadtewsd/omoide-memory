package com.kasakaid.omoidememory.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.Constraints
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import com.kasakaid.omoidememory.data.LocalFile
import com.kasakaid.omoidememory.data.LocalFileRepository
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
        private val omoideMemoryRepository: LocalFileRepository,
        private val driveService: GoogleDriveService,
    ) {
        companion object {
            const val TAG = "GdriveUoloader"
        }

        /**
         * 写真と Video のコンテンツをアップロードする実態
         */
        suspend fun upload(
            pendingFile: LocalFile,
            sourceWorker: WorkManagerTag,
        ): ListenableWorker.Result {
            val tag = "${sourceWorker.value} -> $TAG"
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)

            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                return ListenableWorker.Result.retry()
            }
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
            // first 的な条件で else に来ることはない
            val registeredSecureSsid = omoideUploadPrefsRepository.getSecureWifiSsid()

            if (registeredSecureSsid.isNullOrEmpty()) {
                Log.w(tag, "SSID が構成されいている OK !")
                // Should we fail or succeed?
                // Succeed to stop retrying until user Configures it.
                return ListenableWorker.Result.failure()
            }

            // 4. Upload Files
            try {
                val fileId = driveService.uploadFile(pendingFile)
                if (fileId != null) {
                    omoideMemoryRepository.markAsUploaded(
                        pendingFile.onUploaded(fileId),
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
