package com.kasakaid.omoidememory.ui.maintenance.requestprocess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasakaid.omoidememory.ui.AppRowBarWithBackIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * アップロードレポート詳細画面。
 *
 * @param reportId 表示対象のレポート ID
 * @param onBack 前の画面へ戻るコールバック
 * @param viewModel Hilt 経由で注入される ViewModel
 */
@Composable
fun UploadReportDetailScreen(
    reportId: Long,
    onBack: () -> Unit,
    viewModel: UploadReportViewModel = hiltViewModel(),
) {
    val reportFlow = remember(reportId) { viewModel.getReport(id = reportId) }
    val report by reportFlow.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // レポートが存在しない、または削除された場合は画面を閉じる
    LaunchedEffect(report) {
        if (report == null) {
            // 初回ロードの一瞬 null 対策
        }
    }

    Scaffold(
        topBar = {
            AppRowBarWithBackIcon(
                title = "レポート詳細 (#$reportId)",
                onFinished = onBack,
                actions = {
                    if (report != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "削除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val currentReport = report
        if (currentReport == null) {
            Box(
                modifier =
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "レポートが見つかりません")
            }
        } else {
            ReportDetailContent(
                report = currentReport,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "レポートの削除") },
            text = { Text(text = "レポート #$reportId を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReport(id = reportId)
                        showDeleteDialog = false
                        onBack()
                    },
                ) {
                    Text(text = "削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = "キャンセル")
                }
            },
        )
    }
}

@Composable
private fun ReportDetailContent(
    report: UploadReport,
    modifier: Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
    val createdStr = remember(report.createdAt) { dateFormat.format(Date(report.createdAt)) }
    val updatedStr = remember(report.updatedAt) { dateFormat.format(Date(report.updatedAt)) }
    val elapsedSeconds = remember(report.createdAt, report.updatedAt) { (report.updatedAt - report.createdAt) / 1000 }

    val scrollState = rememberScrollState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(state = scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ステータスサマリーカード
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = report.lastPoint.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = report.lastPoint.color,
                )
                Text(
                    text = "進捗: ${report.successCount} / ${report.totalRequestCount} 件",
                    style = MaterialTheme.typography.titleMedium,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "開始日時: $createdStr",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "更新日時: $updatedStr",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "経過時間: $elapsedSeconds 秒",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // 詳細メッセージ / エラーカード
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "最後のメッセージ / 詳細ログ",
                    style = MaterialTheme.typography.titleMedium,
                )
                SelectionContainer {
                    Text(
                        text = report.message ?: "(メッセージなし)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
