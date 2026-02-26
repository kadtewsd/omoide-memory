package com.kasakaid.omoidememory.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManual
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeUploadingStateByManualTag
import com.kasakaid.omoidememory.worker.AutoGDriveUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        application: Application,
        private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
    ) : ViewModel() {
        private val _isAutoUploadEnabled =
            MutableStateFlow(omoideUploadPrefsRepository.isAutoUploadEnabled())
        val isAutoUploadEnabled: StateFlow<Boolean> = _isAutoUploadEnabled.asStateFlow()

        private val workManager = WorkManager.getInstance(application)
        val isUploading: StateFlow<Boolean> =
            workManager.observeUploadingStateByManualTag(
                viewModelScope = viewModelScope,
            )
        val progress: StateFlow<Pair<Int, Int>?> =
            workManager.observeProgressByManual(
                viewModelScope = viewModelScope,
            )

        fun toggleAutoUpload(enabled: Boolean) {
            omoideUploadPrefsRepository.setAutoUploadEnabled(enabled)
            _isAutoUploadEnabled.value = enabled

            if (enabled) {
                enqueuePeriodicWork()
            } else {
                workManager.cancelUniqueWork("AutoUploadWork")
            }
        }

        /**
         * 自動アップロードをキューイングするメソッド
         */
        private fun enqueuePeriodicWork() {
            // wifi の時だけワーカーを実行してね、という印
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val uploadWorkRequest =
                PeriodicWorkRequestBuilder<AutoGDriveUploadWorker>(1, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

            workManager.enqueueUniquePeriodicWork(
                "AutoUploadWork",
                ExistingPeriodicWorkPolicy.KEEP,
                uploadWorkRequest,
            )
        }
    }
