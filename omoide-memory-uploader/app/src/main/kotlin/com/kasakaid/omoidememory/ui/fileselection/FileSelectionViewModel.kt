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
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeDeletingStateByManualTag
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManual
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManualDelete
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeUploadingStateByManualTag
import com.kasakaid.omoidememory.network.GoogleDriveService
import com.kasakaid.omoidememory.ui.OnOff
import com.kasakaid.omoidememory.worker.LocalFileCleaner
import com.kasakaid.omoidememory.worker.WorkManagerTag
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

enum class FileUploadState(
    val label: String,
) {
    WAITING_FOR_UPLOAD("待ち"),
    UPLOAD_EXCLUDED("除外"),
    UPLOAD_DONE("完了"),
}

enum class DoneFilter(
    val label: String,
) {
    NOT_DELETED("未削除"),
    DELETED("削除済み"),
}

@HiltViewModel
class FileSelectionViewModel
    @Inject
    constructor(
        private val localFileRepository: OmoideMemoryRepository,
        private val excludeOmoideRepository: ExcludeOmoideRepository,
        private val driveService: GoogleDriveService,
        private val localFileCleaner: LocalFileCleaner,
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

        private val deleteResultChannel = Channel<List<Long>>(Channel.BUFFERED)
        val deleteResultEvent = deleteResultChannel.receiveAsFlow()

        private var deleteStarted = false

        init {
            viewModelScope.launch {
                workManager
                    .getWorkInfosForUniqueWorkFlow(WorkManagerTag.ManualDelete.value)
                    .collect { workInfos ->
                        val workInfo = workInfos.firstOrNull() ?: return@collect
                        Log.d("FileSelectionViewModel", "WorkInfo state: ${workInfo.state}, deleteStarted: $deleteStarted")
                        if (!deleteStarted) return@collect

                        when (workInfo.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                deleteStarted = false
                                val deletedIds = workInfo.outputData.getLongArray("DELETED_IDS")?.toList() ?: emptyList()
                                if (deletedIds.isNotEmpty()) {
                                    val targets = localFileRepository.findBy(deletedIds)
                                    localFileRepository.update(targets.map { it.driveDeleted() })
                                }
                                val notDeletedIds = workInfo.outputData.getLongArray("NOT_DELETED_IDS")?.toList() ?: emptyList()
                                deleteResultChannel.send(notDeletedIds)
                            }

                            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                                deleteStarted = false
                                deleteResultChannel.send(emptyList())
                            }

                            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                            }
                        }
                    }
            }
        }

        private val _doneFilter = MutableStateFlow(DoneFilter.NOT_DELETED)
        val doneFilter: StateFlow<DoneFilter> = _doneFilter.asStateFlow()

        fun setDoneFilter(filter: DoneFilter) {
            _doneFilter.value = filter
            _onOff.value = OnOff.Off
            selectedIds.clear()
        }

        private val _deleteRequestEvent = MutableSharedFlow<android.app.PendingIntent>()
        val deleteRequestEvent: SharedFlow<android.app.PendingIntent> = _deleteRequestEvent.asSharedFlow()

        private var pendingDeleteEntities: List<OmoideMemory> = emptyList()

        @OptIn(ExperimentalCoroutinesApi::class)
        val pendingFiles: StateFlow<List<OmoideMemory>> =
            combine(fileUploadState, doneFilter) { mode, filter ->
                mode to filter
            }.flatMapLatest { (mode, filter) ->
                val flow =
                    when (mode) {
                        FileUploadState.WAITING_FOR_UPLOAD -> {
                            localFileRepository
                                .getPotentialPendingFiles()
                                .onEach { file ->
                                    if (selectedIds[file.id] == null) {
                                        selectedIds[file.id] = _onOff.value.isChecked
                                    }
                                }.scan(emptyList()) { acc, value -> acc + value }
                        }

                        FileUploadState.UPLOAD_EXCLUDED -> {
                            localFileRepository.findByAsFlow(UploadState.EXCLUDED)
                        }

                        FileUploadState.UPLOAD_DONE -> {
                            localFileRepository
                                .findByAsFlow(listOf(UploadState.DONE, UploadState.DRIVE_DELETED))
                                .combine(doneFilter) { files, f ->
                                    when (f) {
                                        DoneFilter.NOT_DELETED -> files.filter { it.state == UploadState.DONE }
                                        DoneFilter.DELETED -> files.filter { it.state == UploadState.DRIVE_DELETED }
                                    }
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

        val isUploading: StateFlow<Boolean> =
            workManager.observeUploadingStateByManualTag(viewModelScope = viewModelScope)
        val progress: StateFlow<Pair<Int, Int>?> =
            workManager.observeProgressByManual(viewModelScope = viewModelScope)
        val isDeleting: StateFlow<Boolean> =
            workManager.observeDeletingStateByManualTag(viewModelScope = viewModelScope)
        val deleteProgress: StateFlow<Pair<Int, Int>?> =
            workManager.observeProgressByManualDelete(viewModelScope = viewModelScope)
        val isProcessing: StateFlow<Boolean> =
            combine(isUploading, isDeleting) { uploading, deleting ->
                uploading || deleting
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

        fun startManualUpload(ids: List<Long>) {
            viewModelScope.launch {
                val idSet = ids.toSet()
                val targets =
                    pendingFiles.value
                        .filter { it.id in idSet }
                        .map { it.ready() }
                if (targets.isNotEmpty()) {
                    localFileRepository.add(targets)
                    workManager.enqueueWManualUpload()
                }
            }
        }

        /**
         * 画面上で選択されたコンテンツの操作が完了した際にコールバックとしてコールされる選択状態解除メソッド
         */
        fun clearSelection() {
            selectedIds.clear()
        }

        fun cancelManualUpload() {
            workManager.cancelUniqueWork("manual_upload")
        }

        fun markAsRemoved(ids: List<Long>) {
            viewModelScope.launch {
                val targets = pendingFiles.value.filter { it.id in ids }.map { it.exclude() }
                if (targets.isNotEmpty()) {
                    localFileRepository.add(targets)
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
                val pendingIntent = localFileRepository.deletePhysically(items)
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
                localFileRepository.delete(pendingDeleteEntities.map { it.id })
                pendingDeleteEntities = emptyList()
                selectedIds.clear()
            }
        }

        fun deleteFromDrive(ids: List<Long>) {
            viewModelScope.launch {
                if (ids.isNotEmpty()) {
                    deleteStarted = true
                    workManager.enqueueManualDelete(ids)
                }
            }
        }

        fun cancelDelete() {
            workManager.cancelUniqueWork("manual_delete")
        }
    }
