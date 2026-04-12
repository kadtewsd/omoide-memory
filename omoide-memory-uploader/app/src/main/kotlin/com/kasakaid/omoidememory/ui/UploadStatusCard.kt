package com.kasakaid.omoidememory.ui

import android.app.Application
import android.util.Log
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
import androidx.work.WorkManager
import com.kasakaid.omoidememory.data.OmoideMemoryDao
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.extension.WorkManagerExtension.enqueueWManualUpload
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UploadStatusViewModel
    @Inject
    constructor(
        application: Application,
        private val omoideMemoryRepository: OmoideMemoryRepository,
        omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
        omoideMemoryDao: OmoideMemoryDao,
    ) : ViewModel() {
        /**
         * パーミッション、アカウントの設定が完了してアップロードが行えるか？
         */
        private var canUpload: MutableStateFlow<Boolean> = MutableStateFlow(false)

        fun updateCanUpload(value: Boolean) {
            canUpload.value = value
        }

        /**
         * 変化があった時に画面で描画させるために viewModelScope で枚数を変更する。
         * Flow (リアクティブ) ではなくて直接実行するので
         * 取得するファイルは名前でアップロードしたものと称号をかけるので「おそらくアップロードされていないモノ」を列挙している
         * モバイルの権限、Google のサインインができたらファイル検索をしたい。
         * また検索条件の基準日がユーザーによって変更されたらファイルを変更されたら検索したい。
         * 1. _canUpload ( モバイルの権限、Google のサインイン) は呼び出し元から
         * 2. 検索条件の基準日は、Repository の flow から
         * この 2 つを合成するために, combine を実施しています。
         */
        val pendingFilesCount: StateFlow<Int> =
            combine(
                canUpload, // 現場からの報告（Flow）
                omoideUploadPrefsRepository.getUploadBaseLineInstant(), // リポジトリの蛇口（Flow）
                // 🚀 DBのの変更を監視するFlowを追加！これにより MainScreen で一括アップロードが完了して永続化されたら再描画してくれる。
                omoideMemoryDao.getAllUploadedIdsAsFlow(),
            ) { granted, _, _ ->
                // 許可と基準日のペアを届ける
                if (granted) {
                    omoideMemoryRepository.getPotentialPendingFiles().count()
                } else {
                    0
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0,
            )

        val uploadedCount: StateFlow<Int> =
            omoideMemoryRepository
                .getUploadedCount(listOf(UploadState.DONE, UploadState.DRIVE_DELETED))
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        fun triggerManualUpload() {
            viewModelScope.launch {
                /**
                 * まとめて取得したい時は、last でも first でもなくて、toList。R2DBC の Flux を取り出す時と同じ。
                 */
                workManager.enqueueWManualUpload()
            }
        }

        private val workManager = WorkManager.getInstance(application)

        // WorkInfo から進捗を取り出して StateFlow に変換
        val uploadProgress: StateFlow<Pair<Int, Int>?> =
            workManager.observeProgressByManual(
                viewModelScope = viewModelScope,
            )
    }

@Composable
fun UploadStatusRoute(
    viewModel: UploadStatusViewModel = hiltViewModel(),
    condition: UploadRequiredCondition, // 権限状態などをまとめたオブジェクト
    // 手動アップロードを選択した際の画面遷移先
    onNavigateToContentSelection: () -> Unit,
) {
    val pendingFilesCount by viewModel.pendingFilesCount.collectAsState()
    val uploadedCount by viewModel.uploadedCount.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()

    // パーミッション、サインイン、Wifi状態が変わるたびに、ViewModel の「中の人」に報告する
    LaunchedEffect(condition.canUpload) {
        viewModel.updateCanUpload(condition.canUpload)
    }

    UploadStatusCard(
        pendingFilesCount = pendingFilesCount,
        uploadedCount = uploadedCount,
        condition = condition,
        onUploadClick = {
            viewModel.triggerManualUpload()
        },
        onNavigateToContentSelection = onNavigateToContentSelection,
        progress = uploadProgress,
    )
}

@Composable
fun UploadedContentRoute(
    viewModel: UploadStatusViewModel = hiltViewModel(),
    onNavigateToMaintenance: () -> Unit,
) {
    val uploadedCount by viewModel.uploadedCount.collectAsState()

    UploadedContentCard(
        uploadedCount = uploadedCount,
        onMaintenanceClick = onNavigateToMaintenance,
    )
}

@Composable
fun UploadStatusCard(
    pendingFilesCount: Int,
    uploadedCount: Int,
    condition: UploadRequiredCondition, // 状態を引数で受け取る
    onUploadClick: () -> Unit, // ボタンクリック時のアクション
    onNavigateToContentSelection: () -> Unit,
    progress: Pair<Int, Int>?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            Text("アップロード対象ファイル数: $pendingFilesCount")
            Text("アップロード済み数: $uploadedCount")

            Spacer(modifier = Modifier.height(16.dp))

            // 横並びにする
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp), // ボタン間の隙間
            ) {
                // 「選択」ボタンを左に（サブアクション的な位置付け）
                OutlinedButton( // 種類を変えて「全アップロード」と差別化しても良い
                    onClick = onNavigateToContentSelection,
                    modifier = Modifier.weight(1f),
                    enabled = condition.canUpload,
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text("選択してUP", textAlign = TextAlign.Center)
                }

                // 「全アップロード」を右に（メインアクション）
                Button(
                    onClick = onUploadClick,
                    modifier = Modifier.weight(1f),
                    enabled = condition.canUpload,
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text("すべてUP", textAlign = TextAlign.Center)
                }
            }

            // 進捗バー
            progress?.let { (current, total) ->
                Log.d("進捗バー", "current: $current, total: $total を実行中")
                if (total > 0) {
                    // getWorkInfosByTagFlow で監視している際、状態変化のタイミングによっては、一瞬だけ 「Progress は存在するが、中身が空（デフォルト値の0）」 というデータが UI に流れることがあります。
                    // 0除算が発生する問題を回避するため分岐入れます。
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { current.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "アップロード中: $current / $total",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            condition.getErrorMessage()?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun UploadedContentCard(
    uploadedCount: Int,
    onMaintenanceClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("アップロード済みのコンテンツ", style = MaterialTheme.typography.titleMedium)
            Text("全アップロード数: $uploadedCount")

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onMaintenanceClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("確認")
            }
        }
    }
}
