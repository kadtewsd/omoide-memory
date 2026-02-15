package com.kasakaid.omoidememory.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kasakaid.omoidememory.data.LocalFile
import com.kasakaid.omoidememory.data.LocalFileRepository
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.worker.GdriveUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject


@HiltViewModel
class UploadStatusViewModel @Inject constructor(
    application: Application,
    private val localFileRepository: LocalFileRepository,
    omoideMemoryRepository: OmoideMemoryRepository,
    omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
) : ViewModel() {
    private val _pendingFilesCount = MutableStateFlow(0)
    val pendingFilesCount: StateFlow<Int> = _pendingFilesCount.asStateFlow()

    // UI State
    val uploadedCount: StateFlow<Int> = omoideMemoryRepository.getUploadedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val _baseLineInstant = MutableStateFlow(omoideUploadPrefsRepository.getUploadBaseLineInstant())
    val baseLineInstant: StateFlow<Instant?> = _baseLineInstant.asStateFlow()

    /**
     * 変化があった時に画面で描画させるために viewModelScope で枚数を変更する。
     * Flow (リアクティブ) ではなくて直接実行するので
     * 取得するファイルは名前でアップロードしたものと称号をかけるので「おそらくアップロードされていないモノ」を列挙している
     */
    fun refreshPendingFiles() {
        viewModelScope.launch {
            val pending: List<LocalFile> = localFileRepository.getPotentialPendingFiles()
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
    // WorkInfo から進捗を取り出して StateFlow に変換
    val uploadProgress: StateFlow<Pair<Int, Int>?> =
        workManager.getWorkInfosByTagFlow(GdriveUploadWorker.TAG)
            .map { workInfos ->
                val runningWork = workInfos.find { it.state == WorkInfo.State.RUNNING }
                val progress = runningWork?.progress
                if (progress != null) {
                    val current = progress.getInt("PROGRESS_CURRENT", 0)
                    val total = progress.getInt("PROGRESS_TOTAL", 0)
                    current to total
                } else {
                    null
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}


@Composable
fun UploadStatusRoute(
    viewModel: UploadStatusViewModel = hiltViewModel(),
    canUpload: Boolean, // 権限状態を引数で受け取る
    // 手動アップロードを選択した際の画面遷移先
    onNavigateToContentSelection: () -> Unit,
) {
    val uploadedCount by viewModel.uploadedCount.collectAsState()
    val pendingFilesCount by viewModel.pendingFilesCount.collectAsState()
    val baseLineInstant by viewModel.baseLineInstant.collectAsState()

    LaunchedEffect(canUpload, baseLineInstant) {
        viewModel.refreshPendingFiles()
    }
    UploadStatusCard(
        pendingFilesCount = pendingFilesCount,
        uploadedCount = uploadedCount,
        canUpload = canUpload,
        onUploadClick = {
            viewModel.triggerManualUpload()
        },
        onNavigateToContentSelection = onNavigateToContentSelection,
    )
}

@Composable
fun UploadStatusCard(
    pendingFilesCount: Int,
    uploadedCount: Int,
    canUpload: Boolean, // 権限状態を引数で受け取る
    onUploadClick: () -> Unit,      // ボタンクリック時のアクション
    onNavigateToContentSelection: () -> Unit,
    progress: Pair<Int, Int>? = null, // (current, total)
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

            // 横並びにする
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp) // ボタン間の隙間
            ) {
                // 「選択」ボタンを左に（サブアクション的な位置付け）
                OutlinedButton( // 種類を変えて「全アップロード」と差別化しても良い
                    onClick = onNavigateToContentSelection,
                    modifier = Modifier.weight(1f),
                    enabled = canUpload,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("選択してUP", textAlign = TextAlign.Center)
                }

                // 「全アップロード」を右に（メインアクション）
                Button(
                    onClick = onUploadClick,
                    modifier = Modifier.weight(1f),
                    enabled = canUpload,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("すべてUP", textAlign = TextAlign.Center)
                }

                progress?.let { (current, total) ->
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress =  { current.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "アップロード中: $current / $total",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!canUpload) {
                Text(
                    text = "権限またはサインインが必要です",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
