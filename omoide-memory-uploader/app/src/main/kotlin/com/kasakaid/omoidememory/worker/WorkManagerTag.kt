package com.kasakaid.omoidememory.worker

/**
 * WorkManager が何経由で形動されたか？を表す
 */
sealed interface WorkManagerTag {
    val value: String
    data object Manual: WorkManagerTag {
        override val value: String = GdriveUploadWorker.TAG
    }
    data object Auto: WorkManagerTag {
        override val value: String = AutoGDriveUploadWorker.TAG
    }
}