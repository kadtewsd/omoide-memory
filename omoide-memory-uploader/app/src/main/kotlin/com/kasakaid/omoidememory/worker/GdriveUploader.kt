package com.kasakaid.omoidememory.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.data.WifiSetting
import com.kasakaid.omoidememory.network.GoogleDriveService
import com.kasakaid.omoidememory.network.SaPermissionDriveMetadataProvider
import com.kasakaid.omoidememory.os.CrashReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Gdrive のアップロードの実装
 */
class GdriveUploader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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
        ): Result<String> {
            val tag = "${sourceWorker.value} -> $TAG"

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)

            if (activeNetwork == null || capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
                Log.e(tag, "Upload aborted: Wi-Fi is not connected.")
                val exception = IllegalStateException("Upload aborted: Wi-Fi is not connected.")
                CrashReporter.saveReport(
                    context = context,
                    action = "UPLOAD",
                    throwable = exception,
                )
                return Result.failure(exception)
            }

            cm.bindProcessToNetwork(activeNetwork)

            return try {
                driveService.uploadFile(omoideMemory = pendingFile).fold(
                    onSuccess = { fileId ->
                        Log.d(tag, "Uploaded: ${pendingFile.name}")
                        Result.success(fileId)
                    },
                    onFailure = { e ->
                        Log.e(tag, "Upload Failed for ${pendingFile.name}: ${e.message}")
                        CrashReporter.saveReport(
                            context = context,
                            action = "UPLOAD",
                            throwable = e,
                        )
                        Result.failure(e)
                    },
                )
            } finally {
                cm.bindProcessToNetwork(null)
            }
        }
    }
