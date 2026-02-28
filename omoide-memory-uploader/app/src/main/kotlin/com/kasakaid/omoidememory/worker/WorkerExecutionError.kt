package com.kasakaid.omoidememory.worker

/**
 * Uploader を実行する際に Wifi 関連でエラーが出た際の問題点
 */
sealed interface WorkerExecutionError {
    val message: String

    data object WifiNotConnected : WorkerExecutionError {
        override val message: String = "WiFi is not connected"
    }

    data object SsidNotConfigured : WorkerExecutionError {
        override val message: String = "SSID is not configured"
    }

    class SsidMismatch(
        val current: String?,
        val expected: String,
    ) : WorkerExecutionError {
        override val message: String = "Connected SSID is not the configured secure SSID: $current != $expected"
    }

    data class UploadFailed(
        override val message: String,
    ) : WorkerExecutionError

    class AuthError(
        override val message: String,
    ) : WorkerExecutionError
}
