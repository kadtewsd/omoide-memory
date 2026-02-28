package com.kasakaid.omoidememory.worker

/**
 * Uploader を実行する際に Wifi 関連でエラーが出た際の問題点
 */
sealed interface WorkerExecutionError {
    val message: String

    data object WifiNotConnected : WorkerExecutionError {
        override val message: String = "WiFi is not connected"
    }

    data class UploadFailed(
        override val message: String,
    ) : WorkerExecutionError

    class AuthError(
        override val message: String,
    ) : WorkerExecutionError
}
