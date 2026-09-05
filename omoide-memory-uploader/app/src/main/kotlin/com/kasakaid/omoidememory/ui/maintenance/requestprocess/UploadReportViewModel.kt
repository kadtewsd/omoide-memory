package com.kasakaid.omoidememory.ui.maintenance.requestprocess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.data.UploadReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * アップロードレポート一覧および詳細の取得・削除を担う ViewModel。
 */
@HiltViewModel
class UploadReportViewModel
    @Inject
    constructor(
        private val uploadReportRepository: UploadReportRepository,
    ) : ViewModel() {
        val reports: StateFlow<List<UploadReport>> =
            uploadReportRepository
                .getAllReports()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        fun deleteReport(id: Long) {
            viewModelScope.launch {
                uploadReportRepository.deleteReport(id = id)
            }
        }

        fun deleteAll() {
            viewModelScope.launch {
                uploadReportRepository.deleteAllReports()
            }
        }

        fun getReport(id: Long): StateFlow<UploadReport?> =
            uploadReportRepository
                .getReport(id = id)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = null,
                )
    }
