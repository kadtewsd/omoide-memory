package com.kasakaid.omoidememory.extension

import android.app.Application
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kasakaid.omoidememory.worker.GdriveUploadWorker

object WorkManagerExtension {
    /**
     * application で WorkManager を作ると初期化時に一度だけ取得。以降、この ViewModel 内ではこれを使い回す。
     * そのため、Context は都度作るので、やれるのであれば Application が良い
     */
    fun Application.enqueueWManualUpload(
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
        WorkManager.getInstance(this).enqueueUniqueWork(
            "manual_upload",
            ExistingWorkPolicy.REPLACE, // これで「都度上書き」される
            uploadRequest,
        )
    }
}