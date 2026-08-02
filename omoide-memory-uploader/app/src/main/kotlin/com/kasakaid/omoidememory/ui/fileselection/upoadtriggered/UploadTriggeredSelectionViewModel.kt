package com.kasakaid.omoidememory.ui.fileselection.upoadtriggered

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.extension.WorkManagerExtension.enqueueWManualUpload
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManual
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeUploadingStateByManualTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UploadTriggeredSelectionViewModel
    @Inject
    constructor(
        private val omoideMemoryRepository: OmoideMemoryRepository,
        application: Application,
    ) : ViewModel() {
        val triggeredFiles: StateFlow<List<OmoideMemory>> =
            omoideMemoryRepository
                .findByAsFlow(UploadState.UPLOAD_TRIGGERED)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        private val workManager = WorkManager.getInstance(application)

        val isUploading: StateFlow<Boolean> =
            workManager.observeUploadingStateByManualTag(viewModelScope)

        val progress: StateFlow<Pair<Int, Int>?> =
            workManager.observeProgressByManual(viewModelScope)

        fun resumeUpload() {
            viewModelScope.launch {
                workManager.enqueueWManualUpload()
            }
        }

        fun cancelUpload() {
            workManager.cancelUniqueWork("manual_upload")
        }
    }
