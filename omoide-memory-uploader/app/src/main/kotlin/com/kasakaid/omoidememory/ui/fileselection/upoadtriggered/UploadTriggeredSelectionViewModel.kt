package com.kasakaid.omoidememory.ui.fileselection.upoadtriggered

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.extension.WorkManagerExtension.enqueueWManualUpload
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManual
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeUploadingStateByManualTag
import com.kasakaid.omoidememory.ui.fileselection.UploadResultSummary
import com.kasakaid.omoidememory.worker.WorkManagerTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

        private val uploadResultChannel = Channel<UploadResultSummary>(Channel.BUFFERED)
        val uploadResultEvent = uploadResultChannel.receiveAsFlow()

        private var uploadStarted = false

        init {
            viewModelScope.launch {
                workManager
                    .getWorkInfosForUniqueWorkFlow(WorkManagerTag.Manual.value)
                    .collect { workInfos ->
                        val workInfo = workInfos.firstOrNull() ?: return@collect
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

        fun resumeUpload() {
            uploadStarted = true
            viewModelScope.launch {
                workManager.enqueueWManualUpload()
            }
        }

        fun cancelUpload() {
            workManager.cancelUniqueWork("manual_upload")
        }
    }
