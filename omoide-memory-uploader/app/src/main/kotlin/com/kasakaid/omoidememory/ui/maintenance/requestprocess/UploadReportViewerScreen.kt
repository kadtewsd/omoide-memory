package com.kasakaid.omoidememory.ui.maintenance.requestprocess

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasakaid.omoidememory.ui.AppRowBarWithBackIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * アップロードレポート一覧画面。
 *
 * @param onBack 前の画面へ戻るコールバック
 * @param onNavigateToDetail 詳細画面へ遷移するコールバック
 * @param viewModel Hilt 経由で注入される ViewModel
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UploadReportViewerScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    viewModel: UploadReportViewModel = hiltViewModel(),
) {
    val reports by viewModel.reports.collectAsState()
    var reportToDelete by remember { mutableStateOf<UploadReport?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppRowBarWithBackIcon(
                title = "アップロードレポート確認",
                onFinished = onBack,
                actions = {
                    if (reports.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "全て削除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            if (reports.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "レポートはありません")
                }
            } else {
                LazyColumn {
                    items(items = reports, key = { it.id }) { report ->
                        UploadReportItem(
                            report = report,
                            onClick = { onNavigateToDetail(report.id) },
                            onLongClick = { reportToDelete = report },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // 個別削除ダイアログ
    reportToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { reportToDelete = null },
            title = { Text(text = "レポートの削除") },
            text = { Text(text = "レポート #${target.id} を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReport(id = target.id)
                        reportToDelete = null
                    },
                ) {
                    Text(text = "削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToDelete = null }) {
                    Text(text = "キャンセル")
                }
            },
        )
    }

    // 全削除ダイアログ
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(text = "全てのレポートを削除") },
            text = { Text(text = "全てのアップロードレポートを削除してもよろしいですか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAll()
                        showDeleteAllDialog = false
                    },
                ) {
                    Text(text = "全て削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(text = "キャンセル")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UploadReportItem(
    report: UploadReport,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
    val updateTimeStr = remember(report.updatedAt) { dateFormat.format(Date(report.updatedAt)) }

    ListItem(
        headlineContent = {
            Text(
                text = "#${report.id} - ${report.lastPoint.label}",
                color = report.lastPoint.color,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = "進捗: ${report.successCount} / ${report.totalRequestCount} 件 | 更新: $updateTimeStr",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!report.message.isNullOrEmpty()) {
                    Text(
                        text = report.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        modifier =
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}
