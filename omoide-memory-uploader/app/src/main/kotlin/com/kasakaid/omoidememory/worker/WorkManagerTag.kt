package com.kasakaid.omoidememory.worker

/**
 * WorkManager が何経由で形動されたか？を表す
 */
sealed interface WorkManagerTag {
    val value: String

    data object Manual : WorkManagerTag {
        override val value: String = "manual_upload"
    }

    data object Auto : WorkManagerTag {
        override val value: String = "AutoUploadWork"
    }
}
