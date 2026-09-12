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
import com.kasakaid.omoidememory.extension.WorkManagerExtension.getWorkInfosForUniqueWorkFlow
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeGoogleDriveRequest
import com.kasakaid.omoidememory.ui.fileselection.UploadResultSummary
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.data.UploadReportRepository
import com.kasakaid.omoidememory.worker.GoogleDriveRequestType
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
        omoideMemoryRepository: OmoideMemoryRepository,
        application: Application,
        private val uploadReportRepository: UploadReportRepository,
    ) : ViewModel() {
        private val uploadRequest: GoogleDriveRequestType.Processing = GoogleDriveRequestType.Uploading()

        val triggeredFiles: StateFlow<List<OmoideMemory>> =
            omoideMemoryRepository
                .findByAsFlow(UploadState.UPLOAD_TRIGGERED)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        private val workManager = WorkManager.getInstance(application)

        val activeRequest: StateFlow<GoogleDriveRequestType> =
            workManager.observeGoogleDriveRequest(viewModelScope)

        private val uploadResultChannel = Channel<UploadResultSummary>(Channel.BUFFERED)
        val uploadResultEvent = uploadResultChannel.receiveAsFlow()

        private var uploadStarted = false

        init {
            viewModelScope.launch {
                workManager
                    .getWorkInfosForUniqueWorkFlow(uploadRequest)
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
                workManager.enqueueWManualUpload(uploadReportRepository = uploadReportRepository, contentCount = triggeredFiles.value.size)
            }
        }
    }
