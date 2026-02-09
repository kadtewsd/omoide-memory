package com.kasakaid.pictureuploader.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

import com.kasakaid.pictureuploader.data.OmoideMemoryDao
import com.kasakaid.pictureuploader.data.OmoideMemoryRepository
import com.kasakaid.pictureuploader.data.OmoideUploadPrefsRepository
import com.kasakaid.pictureuploader.data.WifiSetting
import com.kasakaid.pictureuploader.worker.AutoGDriveUploadWorker
import com.kasakaid.pictureuploader.worker.GdriveUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
    private val omoideMemoryRepository: OmoideMemoryRepository
) : ViewModel() {

    private val _isAutoUploadEnabled =
        MutableStateFlow(omoideUploadPrefsRepository.isAutoUploadEnabled())
    val isAutoUploadEnabled: StateFlow<Boolean> = _isAutoUploadEnabled.asStateFlow()

    private val workManager = WorkManager.getInstance(application)
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
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val uploadWorkRequest = PeriodicWorkRequestBuilder<AutoGDriveUploadWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "AutoUploadWork",
            ExistingPeriodicWorkPolicy.KEEP,
            uploadWorkRequest
        )
    }
}
