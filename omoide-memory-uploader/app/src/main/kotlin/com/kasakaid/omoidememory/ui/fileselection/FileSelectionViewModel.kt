package com.kasakaid.omoidememory.ui.fileselection

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kasakaid.omoidememory.data.ExcludeOmoideRepository
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.extension.WorkManagerExtension.enqueueManualDelete
import com.kasakaid.omoidememory.extension.WorkManagerExtension.enqueueWManualUpload
import com.kasakaid.omoidememory.extension.WorkManagerExtension.getWorkInfosForUniqueWorkFlow
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeGoogleDriveRequest
import com.kasakaid.omoidememory.ui.InitialRoute
import com.kasakaid.omoidememory.ui.OnOff
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.data.UploadReportRepository
import com.kasakaid.omoidememory.worker.GoogleDriveRequestType
import com.kasakaid.omoidememory.worker.LocalFileCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ファイルアップロードに関連する画面と
 * - ドメイン上のステータス
 * - 遷移仕様
 * をまとめる
 */
enum class FileUploadState(
    val label: String,
    val targetStates: List<UploadState>,
    val route: String,
) {
    WAITING_FOR_UPLOAD(
        label = "待ち",
        targetStates = emptyList(),
        route = InitialRoute.WAITING_FOR_UPLOAD.route,
    ),
    UPLOAD_EXCLUDED(
        label = "除外",
        targetStates = listOf(UploadState.EXCLUDED),
        route = InitialRoute.UPLOAD_EXCLUDED.route,
    ),
    UPLOAD_DONE(
        label = "完了",
        targetStates = listOf(UploadState.DONE, UploadState.DRIVE_DELETED, UploadState.DELETE_TRIGGERED),
        route = InitialRoute.UPLOAD_DONE.route,
    ),
}

/**
 * アップロード済み・削除済みのタブ切り替え時に利用するコンテンツのフィルタ
 */
enum class TabFilter(
    val label: String,
    private val filterState: Set<UploadState>,
) {
    // 【未削除タブ】
    // Drive上に残っているファイル（DONE）を表示する。
    // また、削除実行中（DELETE_TRIGGERED）のファイルもここに含める。
    // （これを含めないと、削除ボタンを押した瞬間に一覧から消えてしまいUIがガタつくため、
    //   Worker による Drive からの削除が完了するまで「未削除」側に留めておく）
    NOT_DELETED(label = "未削除", filterState = setOf(UploadState.DONE, UploadState.DELETE_TRIGGERED)),

    // 【削除済みタブ】
    // Worker による Drive 上の物理削除が成功したファイル（DRIVE_DELETED）のみを表示する
    DELETED(label = "削除済み", filterState = setOf(UploadState.DRIVE_DELETED)),
    ;

    fun filter(memories: List<OmoideMemory>): List<OmoideMemory> = memories.filter { filterState.contains(it.state) }
}

data class UploadResultSummary(
    val pendingCount: Int,
    val errorMessage: String?,
)

@HiltViewModel
class FileSelectionViewModel
    @Inject
    constructor(
        private val omoideMemoryRepository: OmoideMemoryRepository,
        private val excludeOmoideRepository: ExcludeOmoideRepository,
        private val localFileCleaner: LocalFileCleaner,
        private val uploadReportRepository: UploadReportRepository,
        application: Application,
    ) : ViewModel() {
        private val _fileUploadState = MutableStateFlow(FileUploadState.WAITING_FOR_UPLOAD)
        val fileUploadState: StateFlow<FileUploadState> = _fileUploadState.asStateFlow()

        fun setSelectionMode(mode: FileUploadState) {
            _fileUploadState.value = mode
            _onOff.value = OnOff.Off
            selectedIds.clear()
        }

        fun initMode(mode: FileUploadState) {
            _fileUploadState.value = mode
            _onOff.value = OnOff.Off
            selectedIds.clear()
        }

        private val workManager = WorkManager.getInstance(application)
        private val uploadRequest: GoogleDriveRequestType.Processing = GoogleDriveRequestType.Uploading()
        private val deleteRequest: GoogleDriveRequestType.Processing = GoogleDriveRequestType.Deleting()

        private val deleteResultChannel = Channel<Int>(Channel.BUFFERED)
        val deleteResultEvent = deleteResultChannel.receiveAsFlow()

        private val uploadResultChannel = Channel<UploadResultSummary>(Channel.BUFFERED)
        val uploadResultEvent = uploadResultChannel.receiveAsFlow()

        private var deleteStarted = false
        private var uploadStarted = false

        init {
            viewModelScope.launch {
                workManager
                    .getWorkInfosForUniqueWorkFlow(deleteRequest)
                    .collect { workInfos ->
                        val workInfo = workInfos.firstOrNull() ?: return@collect
                        Log.d("FileSelectionViewModel", "WorkInfo state: ${workInfo.state}, deleteStarted: $deleteStarted")
                        if (!deleteStarted) return@collect

                        when (workInfo.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                deleteStarted = false
                                val notDeletedCount = workInfo.outputData.getInt("NOT_DELETED_COUNT", 0)
                                deleteResultChannel.send(notDeletedCount)
                            }

                            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                                deleteStarted = false
                                deleteResultChannel.send(0)
                            }

                            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                            }
                        }
                    }
            }
            viewModelScope.launch {
                workManager
                    .getWorkInfosForUniqueWorkFlow(uploadRequest)
                    .collect { workInfos ->
                        val workInfo = workInfos.firstOrNull() ?: return@collect
                        Log.d("FileSelectionViewModel", "ManualUpload WorkInfo state: ${workInfo.state}, uploadStarted: $uploadStarted")
                        if (!uploadStarted) return@collect

                        when (workInfo.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                uploadStarted = false
                                val pendingCount = workInfo.outputData.getInt("PENDING_COUNT", 0)
                                uploadResultChannel.send(
                                    UploadResultSummary(
                                        pendingCount = pendingCount,
                                        errorMessage = null,
                                    ),
                                )
                            }

                            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                                uploadStarted = false
                                val pendingCount = workInfo.outputData.getInt("PENDING_COUNT", 0)
                                val errorMessage = workInfo.outputData.getString("ERROR_MESSAGE")
                                uploadResultChannel.send(
                                    UploadResultSummary(
                                        pendingCount = pendingCount,
                                        errorMessage = errorMessage,
                                    ),
                                )
                            }

                            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                            }
                        }
                    }
            }
        }

        private val _tabFilter = MutableStateFlow(TabFilter.NOT_DELETED)
        val tabFilter: StateFlow<TabFilter> = _tabFilter.asStateFlow()

        fun setDoneFilter(filter: TabFilter) {
            _tabFilter.value = filter
            _onOff.value = OnOff.Off
            selectedIds.clear()
        }

        private val _deleteRequestEvent = MutableSharedFlow<android.app.PendingIntent>()
        val deleteRequestEvent: SharedFlow<android.app.PendingIntent> = _deleteRequestEvent.asSharedFlow()

        private var pendingDeleteEntities: List<OmoideMemory> = emptyList()

        @OptIn(ExperimentalCoroutinesApi::class)
        val pendingFiles: StateFlow<List<OmoideMemory>> =
            combine(fileUploadState, tabFilter) { mode, filter ->
                mode to filter
            }.flatMapLatest { (mode, _) ->
                val flow =
                    when (mode) {
                        FileUploadState.WAITING_FOR_UPLOAD -> {
                            omoideMemoryRepository
                                .getPotentialPendingFiles()
                                .onEach { file ->
                                    if (selectedIds[file.id] == null) {
                                        selectedIds[file.id] = _onOff.value.isChecked
                                    }
                                }.scan(emptyList()) { acc, value -> acc + value }
                        }

                        FileUploadState.UPLOAD_EXCLUDED -> {
                            omoideMemoryRepository.findByAsFlow(mode.targetStates)
                        }

                        FileUploadState.UPLOAD_DONE -> {
                            // 「アップロード完了」画面では、「未削除」と「削除済み」のサブフィルタ（DoneFilter）で表示を切り替える
                            omoideMemoryRepository
                                .findByAsFlow(mode.targetStates)
                                .combine(tabFilter) { files, f ->
                                    f.filter(files)
                                }
                        }
                    }
                flow.map { files ->
                    localFileCleaner.cleanUpAndFilter(
                        files = files,
                        currentMode = mode,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        val selectedIds = mutableStateMapOf<Long, Boolean>()

        fun toggleSelection(id: Long) {
            selectedIds[id] = !(selectedIds[id] ?: false)
        }

        private val _onOff: MutableStateFlow<OnOff> = MutableStateFlow(OnOff.Off)
        val onOff: StateFlow<OnOff> = _onOff.asStateFlow()

        fun toggleAll(onOff: OnOff) {
            _onOff.value = onOff
            pendingFiles.value.forEach { file ->
                selectedIds[file.id] = onOff.isChecked
            }
        }

        val activeRequest: StateFlow<GoogleDriveRequestType> =
            workManager.observeGoogleDriveRequest(viewModelScope = viewModelScope)

        fun startManualUpload(ids: List<Long>) {
            viewModelScope.launch {
                val idSet = ids.toSet()
                val targets =
                    pendingFiles.value
                        .filter { it.id in idSet }
                        .map { it.triggered() }
                if (targets.isNotEmpty()) {
                    uploadStarted = true
                    omoideMemoryRepository.upsert(targets)
                    workManager.enqueueWManualUpload(uploadReportRepository = uploadReportRepository, contentCount = targets.size)
                }
            }
        }

        /**
         * 画面上で選択されたコンテンツの操作が完了した際にコールバックとしてコールされる選択状態解除メソッド
         */
        fun clearSelection() {
            selectedIds.clear()
        }

        fun markAsRemoved(ids: List<Long>) {
            viewModelScope.launch {
                val targets = pendingFiles.value.filter { it.id in ids }.map { it.exclude() }
                if (targets.isNotEmpty()) {
                    omoideMemoryRepository.upsert(targets)
                    selectedIds.clear()
                }
            }
        }

        fun revive(ids: List<Long>) {
            viewModelScope.launch {
                excludeOmoideRepository.revive(ids)
            }
        }

        fun deletePhysically(items: List<OmoideMemory>) {
            viewModelScope.launch {
                val pendingIntent = omoideMemoryRepository.deletePhysically(items)
                if (pendingIntent != null) {
                    pendingDeleteEntities = items
                    _deleteRequestEvent.emit(pendingIntent)
                } else {
                    selectedIds.clear()
                }
            }
        }

        fun deleteAfterPermission() {
            viewModelScope.launch {
                omoideMemoryRepository.delete(pendingDeleteEntities.map { it.id }.toSet())
                pendingDeleteEntities = emptyList()
                selectedIds.clear()
            }
        }

        fun deleteFromDrive(ids: Set<Long>) {
            viewModelScope.launch {
                if (ids.isNotEmpty()) {
                    deleteStarted = true
                    omoideMemoryRepository.updateState(ids, UploadState.DELETE_TRIGGERED)
                    workManager.enqueueManualDelete()
                }
            }
        }
    }
