package com.kasakaid.omoidememory.worker

import androidx.work.WorkManager
import com.kasakaid.omoidememory.ui.indicator.Progress

/**
 * Google Drive に対する非同期リクエストの種別および状態を表現する sealed interface。
 *
 * アップロード（[Uploading]）および削除（[Deleting]）の状態・進捗・キャンセル処理を抽象化し、
 * UI や ViewModel でパターンマッチ（when）により統一的に扱えるようにします。
 */
sealed interface GoogleDriveRequestType {
    sealed interface Processing : GoogleDriveRequestType {
        val label: String
        val requestProgress: Progress
        val workerName: String

        fun onCancel()
    }

    data class Uploading(
        override val requestProgress: Progress = Progress(0, 0),
        private val onCancelAction: () -> Unit = {},
    ) : Processing {
        override val label: String = "アップロード中"
        override val workerName: String = "manual_upload"

        constructor(requestProgress: Progress, workManager: WorkManager) : this(
            requestProgress = requestProgress,
            onCancelAction = { workManager.cancelUniqueWork("manual_upload") },
        )

        override fun onCancel() = onCancelAction()
    }

    data class Deleting(
        override val requestProgress: Progress = Progress(0, 0),
        private val onCancelAction: () -> Unit = {},
    ) : Processing {
        override val label: String = "削除中"
        override val workerName: String = "manual_delete"

        constructor(requestProgress: Progress, workManager: WorkManager) : this(
            requestProgress = requestProgress,
            onCancelAction = { workManager.cancelUniqueWork("manual_delete") },
        )

        override fun onCancel() = onCancelAction()
    }

    data object None : GoogleDriveRequestType
}
