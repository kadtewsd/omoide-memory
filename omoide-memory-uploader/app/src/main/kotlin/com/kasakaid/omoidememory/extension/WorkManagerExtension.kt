package com.kasakaid.omoidememory.extension

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kasakaid.omoidememory.worker.GdriveUploadWorker
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
    fun WorkManager.enqueueWManualUpload(
        hashes: Array<String>,
        totalCount: Int,
    ) {
        val workData = workDataOf(
            "TARGET_HASHES" to hashes,
            "TOTAL_COUNT" to totalCount,
        )
        val uploadRequest = OneTimeWorkRequestBuilder<GdriveUploadWorker>()
            .setInputData(workData)
            .addTag(GdriveUploadWorker.TAG)
            .build()
        val tag = "FileSelectionRoute"
        Log.d(tag, "選択されたhash ${hashes.size}件")

        // enqueueUniqueWork + REPLACE は 「名前（Unique Name）」を指定することで、ひとつの管理枠を作ります。
        // 唯一性の保証: 同じ名前のジョブがすでにキューにある場合、WorkManager が介入します。
        //REPLACE の魔法: 新しいリクエストが来たら、**古い方を即座にキャンセル（中断）**して、新しい方を最初から実行します。
        enqueueUniqueWork(
            "manual_upload",
            ExistingWorkPolicy.REPLACE, // これで「都度上書き」される
            uploadRequest,
        )
    }

    /**
     * アップロード状態を監視します。
     * 現在の進捗とほぼ同じですが、念の為 Worker のステートで確認
     */
    fun WorkManager.observeUploadingState(viewModelScope: CoroutineScope): StateFlow<Boolean> {
        return getWorkInfosByTagFlow(GdriveUploadWorker.TAG)
            .map { infos ->
                infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    }

    /**
     * 現在の進捗を確認します。
     */
    fun WorkManager.observeProgress(viewModelScope: CoroutineScope): StateFlow<Pair<Int, Int>?> {
        return getWorkInfosByTagFlow(GdriveUploadWorker.TAG).map { workInfos ->
            Log.d("アップロード監視", "${workInfos.size}件のワークフロー")
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
}