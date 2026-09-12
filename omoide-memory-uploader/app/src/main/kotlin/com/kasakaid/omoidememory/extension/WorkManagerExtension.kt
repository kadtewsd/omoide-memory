package com.kasakaid.omoidememory.extension

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kasakaid.omoidememory.ui.indicator.Progress
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.UploadReport
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.data.UploadReportRepository
import com.kasakaid.omoidememory.worker.GdriveDeleteWorker
import com.kasakaid.omoidememory.worker.GdriveUploadWorker
import com.kasakaid.omoidememory.worker.GoogleDriveRequestType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

object GdriveUploadWorkerKeys {
    const val KEY_REPORT_ID = "REPORT_ID"
}

object WorkManagerExtension {
    /**
     * application で WorkManager を作ると初期化時に一度だけ取得。以降、この ViewModel 内ではこれを使い回す。
     * そのため、Context は都度作るので、やれるのであれば Application が良い。
     * 利用元は、application を指定することを想定
     */
    suspend fun WorkManager.enqueueWManualUpload(
        uploadReportRepository: UploadReportRepository,
        contentCount: Int,
    ) {
        val reportId = uploadReportRepository.add(UploadReport.initial(contentCount))
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // 🚀 手動の場合はとにかく動かして、Wi-Fi 未接続なら Uploader 側でエラーを出す
                .build()

        val uploadRequest =
            OneTimeWorkRequestBuilder<GdriveUploadWorker>()
                .addTag(GdriveUploadWorker.TAG)
                .setConstraints(constraints)
                .setInputData(workDataOf(GdriveUploadWorkerKeys.KEY_REPORT_ID to reportId))
                .build()
        val tag = "FileSelectionRoute"
        Log.d(tag, "手動アップロードをキューに入れました (REPLACE)")

        // enqueueUniqueWork + REPLACE は 「名前（Unique Name）」を指定することで、ひとつの管理枠を作ります。
        // 唯一性の保証: 同じ名前のジョブがすでにキューにある場合、WorkManager が介入します。
        // REPLACE の魔法: 前のリクエストが完了していない場合はアボートしてあと勝ち。
        enqueueUniqueWork(
            GoogleDriveRequestType.Uploading().workerName,
            ExistingWorkPolicy.REPLACE,
            uploadRequest,
        )
    }

    fun WorkManager.enqueueManualDelete() {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

        val deleteRequest =
            OneTimeWorkRequestBuilder<GdriveDeleteWorker>()
                .addTag(GdriveDeleteWorker.TAG)
                .setConstraints(constraints)
                .build()

        enqueueUniqueWork(
            GoogleDriveRequestType.Deleting().workerName,
            ExistingWorkPolicy.REPLACE,
            deleteRequest,
        )
    }

    /**
     * 指定されたリクエスト種別 ([GoogleDriveRequestType.Processing]) の UniqueWorkFlow を取得します。
     */
    fun WorkManager.getWorkInfosForUniqueWorkFlow(requestType: GoogleDriveRequestType.Processing): Flow<List<WorkInfo>> =
        getWorkInfosForUniqueWorkFlow(requestType.workerName)

    /**
     * 指定されたワーカー名 (Unique Work Name) の実行状態 (RUNNING または ENQUEUED) を監視します。
     */
    fun WorkManager.observeRequestState(
        workerName: String,
        viewModelScope: CoroutineScope,
    ): StateFlow<Boolean> =
        getWorkInfosForUniqueWorkFlow(workerName)
            .map { infos ->
                infos.any { it.state.isActive() }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * 指定されたワーカー名 (Unique Work Name) の進捗を監視します。
     */
    fun WorkManager.observeProgress(
        workerName: String,
        viewModelScope: CoroutineScope,
    ): StateFlow<Progress?> =
        getWorkInfosForUniqueWorkFlow(workerName)
            .map { it.progress() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * 指定された生成関数 [create] から得られるワーカーを監視し、進捗から [GoogleDriveRequestType.Processing] を生成するフローを返します。
     */
    private fun <T : GoogleDriveRequestType.Processing> WorkManager.observeRequest(create: (Progress) -> T): Flow<T?> {
        val workerName = create(Progress(0, 0)).workerName
        return getWorkInfosForUniqueWorkFlow(workerName)
            .map { it.requestType(create) }
    }

    /**
     * Google Drive に対するアクティブな非同期リクエスト ([GoogleDriveRequestType]) を監視します。
     * アップロード中または削除中のリクエストをパターンマッチ可能な単一のフローとして提供します。
     */
    fun WorkManager.observeGoogleDriveRequest(viewModelScope: CoroutineScope): StateFlow<GoogleDriveRequestType> {
        val uploadFlow =
            observeRequest { progress ->
                GoogleDriveRequestType.Uploading(
                    requestProgress = progress,
                    workManager = this@observeGoogleDriveRequest,
                )
            }

        val deleteFlow =
            observeRequest { progress ->
                GoogleDriveRequestType.Deleting(
                    requestProgress = progress,
                    workManager = this@observeGoogleDriveRequest,
                )
            }

        return combine(uploadFlow, deleteFlow) { upload, delete ->
            upload ?: delete ?: GoogleDriveRequestType.None
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoogleDriveRequestType.None)
    }

    /**
     * アップロード状態を監視します。
     */
    fun WorkManager.observeUploadingState(viewModelScope: CoroutineScope): StateFlow<Boolean> =
        observeRequestState(
            workerName = GoogleDriveRequestType.Uploading().workerName,
            viewModelScope = viewModelScope,
        )

    /**
     * アップロード進捗を監視します。
     */
    fun WorkManager.observeUploadProgress(viewModelScope: CoroutineScope): StateFlow<Progress?> =
        observeProgress(
            workerName = GoogleDriveRequestType.Uploading().workerName,
            viewModelScope = viewModelScope,
        )
}

/**
 * アクティブ扱いかを判断する
 */
fun WorkInfo.State.isActive(): Boolean = setOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED).contains(this)

/**
 * アクティブな WorkInfo から進捗情報（[Progress]）を取り出します。
 */
fun List<WorkInfo>.progress(): Progress? {
    val activeWork = this.find { it.state.isActive() } ?: return null
    val p = activeWork.progress
    val current = p.getInt("PROGRESS_CURRENT", 0)
    val total = p.getInt("PROGRESS_TOTAL", 0)
    return Progress(progressed = current, total = total)
}

/**
 * アクティブな WorkInfo の進捗情報から高階関数 [create] を通じて [GoogleDriveRequestType] を生成します。
 */
fun <T : GoogleDriveRequestType> List<WorkInfo>.requestType(create: (Progress) -> T): T? = progress()?.let(create)
