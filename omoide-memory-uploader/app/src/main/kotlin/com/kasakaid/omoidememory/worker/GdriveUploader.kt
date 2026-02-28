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
        private val wifiRepository: WifiRepository,
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

            /**
             * 【背景と制約】
             * ワーカー実行中（アップロード処理中）は、指定された SSID との一致まではチェックせず、
             * 物理的に何らかの Wi-Fi に接続されていることのみを確認する仕様としています。
             *
             * [理由]
             * 1. Android の仕様上、SSID の取得には「位置情報 (GPS)」の権限と有効化が必須です。
             * 2. バックグラウンド実行時やスリープ中は、OS の省電力制御やプライバシー保護により、
             *    Wi-Fi 接続自体は維持されていても SSID の取得のみが失敗（<unknown ssid>）するケースが多発します。
             * 3. 厳密な SSID チェックを継続すると、連投アップロード中に不自然に中断される原因となるため、
             *    「ワーカーが走り始めた＝開始時に正当な Wi-Fi 内にいた」とみなし、実行中は Wi-Fi 切断のみを監視します。
             *
             * ※ セキュリティ上の制約より厳密さを求める場合は、開始時だけでなく各ファイル毎に
             *    snapshotSsid() を呼び出す必要がありますが、現状はユーザビリティと安定性を優先しています。
             */
            if (!wifiRepository.isConnectedToWifi()) {
                Log.w(tag, "Wi-Fi に接続されていません。アップロードを中断します。")
                return WorkerExecutionError.WifiNotConnected.left()
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
