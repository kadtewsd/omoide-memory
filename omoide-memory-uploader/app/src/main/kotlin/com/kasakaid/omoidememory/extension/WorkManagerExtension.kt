package com.kasakaid.omoidememory.extension

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kasakaid.omoidememory.worker.GdriveUploadWorker
import com.kasakaid.omoidememory.worker.WorkManagerTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

object WorkManagerExtension {
    /**
     * application で WorkManager を作ると初期化時に一度だけ取得。以降、この ViewModel 内ではこれを使い回す。
     * そのため、Context は都度作るので、やれるのであれば Application が良い。
     * 利用元は、application を指定することを想定
     */
    fun WorkManager.enqueueWManualUpload() {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

        val uploadRequest =
            OneTimeWorkRequestBuilder<GdriveUploadWorker>()
                .addTag(GdriveUploadWorker.TAG)
                .setConstraints(constraints)
                .build()
        val tag = "FileSelectionRoute"
        Log.d(tag, "手動アップロードをキューに入れました")

        // enqueueUniqueWork + REPLACE は 「名前（Unique Name）」を指定することで、ひとつの管理枠を作ります。
        // 唯一性の保証: 同じ名前のジョブがすでにキューにある場合、WorkManager が介入します。
        // KEEP の魔法: 前のリクエストが完了していない場合は何もしない
        enqueueUniqueWork(
            "manual_upload",
            ExistingWorkPolicy.KEEP,
            uploadRequest,
        )
    }

    /**
     * アップロード状態を監視します。
     * 現在の進捗とほぼ同じですが、念の為 Worker のステートで確認
     */
    fun WorkManager.observeUploadingStateByManualTag(viewModelScope: CoroutineScope): StateFlow<Boolean> =
        observeUploadingState(
            viewModelScope = viewModelScope,
            workManagerTag = WorkManagerTag.Manual,
        )

    private fun WorkManager.observeUploadingState(
        viewModelScope: CoroutineScope,
        workManagerTag: WorkManagerTag,
    ): StateFlow<Boolean> =
        getWorkInfosForUniqueWorkFlow(workManagerTag.value)
            .map { infos ->
                infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun WorkManager.observeProgressByManual(viewModelScope: CoroutineScope): StateFlow<Pair<Int, Int>?> =
        observeProgress(
            viewModelScope = viewModelScope,
            workManagerTag = WorkManagerTag.Manual,
        )

    /**
     * 現在の進捗を確認します。
     */
    private fun WorkManager.observeProgress(
        viewModelScope: CoroutineScope,
        workManagerTag: WorkManagerTag,
    ): StateFlow<Pair<Int, Int>?> =
        getWorkInfosForUniqueWorkFlow(workManagerTag.value)
            .map { workInfos ->
                val runningWork = workInfos.find { it.state == WorkInfo.State.RUNNING }
                val progress = runningWork?.progress
                if (progress != null) {
                    val current = progress.getInt("PROGRESS_CURRENT", 0)
                    val total = progress.getInt("PROGRESS_TOTAL", 0)
                    current to total
                } else {
                    null
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
