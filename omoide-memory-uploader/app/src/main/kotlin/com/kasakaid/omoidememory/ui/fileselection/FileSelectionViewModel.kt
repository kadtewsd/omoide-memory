package com.kasakaid.omoidememory.ui.fileselection

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SelectionMode(
    val label: String,
) {
    TARGET("待ち"),
    EXCLUDED("除外"),
    DONE("完了"),
}

enum class DoneFilter(
    val label: String,
) {
    ALL("すべて"),
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
        application: Application,
    ) : ViewModel() {
        private val _selectionMode = MutableStateFlow(SelectionMode.TARGET)
        val selectionMode: StateFlow<SelectionMode> = _selectionMode.asStateFlow()

        fun setSelectionMode(mode: SelectionMode) {
            _selectionMode.value = mode
            selectedIds.clear()
        }

        fun initMode(mode: SelectionMode) {
            _selectionMode.value = mode
        }

        private val _doneFilter = MutableStateFlow(DoneFilter.ALL)
        val doneFilter: StateFlow<DoneFilter> = _doneFilter.asStateFlow()

        fun setDoneFilter(filter: DoneFilter) {
            _doneFilter.value = filter
            selectedIds.clear()
        }

        private val _deleteRequestEvent = MutableSharedFlow<android.app.PendingIntent>()
        val deleteRequestEvent: SharedFlow<android.app.PendingIntent> = _deleteRequestEvent.asSharedFlow()

        private var pendingDeleteEntities: List<OmoideMemory> = emptyList()

        @OptIn(ExperimentalCoroutinesApi::class)
        val pendingFiles: StateFlow<List<OmoideMemory>> =
            combine(selectionMode, doneFilter, localFileRepository.getAllUploadedIdsAsFlow()) { mode, filter, _ ->
                mode to filter
            }.flatMapLatest { (mode, filter) ->
                when (mode) {
                    SelectionMode.TARGET -> {
                        localFileRepository
                            .getPotentialPendingFiles()
                            .onEach { file ->
                                if (selectedIds[file.id] == null) {
                                    selectedIds[file.id] = _onOff.value.isChecked
                                }
                            }.scan(emptyList()) { acc, value -> acc + value }
                    }

                    SelectionMode.EXCLUDED -> {
                        localFileRepository.findByAsFlow(UploadState.EXCLUDED)
                    }

                    SelectionMode.DONE -> {
                        localFileRepository
                            .findByAsFlow(listOf(UploadState.DONE, UploadState.DRIVE_DELETED))
                            .combine(doneFilter) { files, f ->
                                when (f) {
                                    DoneFilter.ALL -> files
                                    DoneFilter.NOT_DELETED -> files.filter { it.state == UploadState.DONE }
                                    DoneFilter.DELETED -> files.filter { it.state == UploadState.DRIVE_DELETED }
                                }
                            }
                    }
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
            val selectableIds =
                if (selectionMode.value == SelectionMode.DONE) {
                    pendingFiles.value
                        .filter { it.state == UploadState.DONE }
                        .map { it.id }
                        .toSet()
                } else {
                    selectedIds.keys
                }
            selectedIds.forEach { (hash, _) ->
                if (hash in selectableIds) {
                    selectedIds[hash] = onOff.isChecked
                } else {
                    selectedIds[hash] = false
                }
            }
        }

        private val workManager = WorkManager.getInstance(application)
        val isUploading: StateFlow<Boolean> =
            workManager.observeUploadingStateByManualTag(viewModelScope = viewModelScope)
        val progress: StateFlow<Pair<Int, Int>?> =
            workManager.observeProgressByManual(viewModelScope = viewModelScope)
        val isDeleting: StateFlow<Boolean> =
            workManager.observeDeletingStateByManualTag(viewModelScope = viewModelScope)
        val deleteProgress: StateFlow<Pair<Int, Int>?> =
            workManager.observeProgressByManualDelete(viewModelScope = viewModelScope)

        fun startManualUpload(ids: List<Long>) {
            viewModelScope.launch {
                val idSet = ids.toSet()
                val targets =
                    pendingFiles.value
                        .filter { it.id in idSet }
                        .map { it.ready() }
                if (targets.isNotEmpty()) {
                    localFileRepository.add(targets)
                    selectedIds.clear()
                    workManager.enqueueWManualUpload()
                }
            }
        }

        fun cancelManualUpload() {
            workManager.cancelUniqueWork("manual_upload")
        }

        fun markAsRemoved(ids: List<Long>) {
            viewModelScope.launch {
                val targets = pendingFiles.value.filter { it.id in ids }.map { it.exclude() }
                if (targets.isNotEmpty()) {
                    localFileRepository.add(targets)
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
                    workManager.enqueueManualDelete(ids)
                    selectedIds.clear()
                }
            }
        }

        fun cancelDelete() {
            workManager.cancelUniqueWork("manual_delete")
        }
    }
