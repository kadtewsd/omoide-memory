package com.kasakaid.pictureuploader.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kasakaid.pictureuploader.data.LocalFile
import com.kasakaid.pictureuploader.data.OmoideMemoryRepository
import com.kasakaid.pictureuploader.worker.GdriveUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class UploadStatusViewModel @Inject constructor(
    application: Application,
    private val omoideMemoryRepository: OmoideMemoryRepository,
) : ViewModel() {
    private val _pendingFilesCount = MutableStateFlow(0)
    val pendingFilesCount: StateFlow<Int> = _pendingFilesCount.asStateFlow()

    // UI State
    val uploadedCount: StateFlow<Int> = omoideMemoryRepository.getUploadedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)


    /**
     * 変化があった時に画面で描画させるために viewModelScope で枚数を変更する。
     * Flow (リアクティブ) ではなくて直接実行するので
     */
    fun refreshPendingFiles() {
        viewModelScope.launch {
            val pending: List<LocalFile> = omoideMemoryRepository.getPendingFiles()
            _pendingFilesCount.value = pending.size
        }
    }


    private val workManager = WorkManager.getInstance(application)

    fun triggerManualUpload() {
        val uploadWorkRequest = OneTimeWorkRequestBuilder<GdriveUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()

        workManager.enqueue(uploadWorkRequest)
    }
}


@Composable
fun UploadStatusRoute(
    viewModel: UploadStatusViewModel = hiltViewModel(),
    canUpload: Boolean, // 権限状態を引数で受け取る
) {
    val uploadedCount by viewModel.uploadedCount.collectAsState()
    val pendingFilesCount by viewModel.pendingFilesCount.collectAsState()

    LaunchedEffect(canUpload) {
        viewModel.refreshPendingFiles()
    }
    UploadStatusCard(
        pendingFilesCount = pendingFilesCount,
        uploadedCount = uploadedCount,
        canUpload = canUpload,
        onUploadClick = {
            viewModel.triggerManualUpload()
        }
    )
}

@Composable
fun UploadStatusCard(
    pendingFilesCount: Int,
    uploadedCount: Int,
    canUpload: Boolean, // 権限状態を引数で受け取る
    onUploadClick: () -> Unit      // ボタンクリック時のアクション
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            Text("アップロード対象ファイル数: $pendingFilesCount")
            Text("全アップロード数: $uploadedCount")

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onUploadClick,
                modifier = Modifier.fillMaxWidth(),
                // ここがポイント：権限がない場合は false になる
                enabled = canUpload
            ) {
                // ボタン内のテキストも状態に合わせて変えると親切です
                Text(if (canUpload) "手動でアップロードする" else "権限または Google へのサインインが必要です")
            }
        }
    }
}