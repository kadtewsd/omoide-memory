package com.kasakaid.omoidememory.worker

import android.util.Log
import androidx.work.Constraints
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.network.GoogleDriveService
import javax.inject.Inject

/**
 * Gdrive のアップロードの実装
 */
class GdriveUploader
    @Inject
    constructor(
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
