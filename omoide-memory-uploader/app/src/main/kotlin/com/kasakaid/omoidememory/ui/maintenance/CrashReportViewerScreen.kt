package com.kasakaid.omoidememory.ui.maintenance

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kasakaid.omoidememory.os.CrashReporter
import com.kasakaid.omoidememory.ui.AppRowBarWithBackIcon
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CrashReportViewerScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
) {
    val context = LocalContext.current
    var reports by remember { mutableStateOf(CrashReporter.getReportFiles(context)) }
    var reportToDelete by remember { mutableStateOf<File?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    fun refresh() {
        reports = CrashReporter.getReportFiles(context)
    }

    Scaffold(
        topBar = {
            AppRowBarWithBackIcon(
                title = "スタックトレース確認",
                onFinished = onBack,
                actions = {
                    if (reports.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "全て削除")
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
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text("レポートはありません")
                }
            } else {
                LazyColumn {
                    items(reports) { report ->
                        ReportItem(
                            report = report,
                            onClick = { onNavigateToDetail(report.name) },
                            onLongClick = { reportToDelete = report },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // 個別削除ダイアログ
    reportToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { reportToDelete = null },
            title = { Text("レポートの削除") },
            text = { Text("このレポートを削除しますか？\n${file.name}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        CrashReporter.deleteReport(file)
                        refresh()
                        reportToDelete = null
                    },
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToDelete = null }) {
                    Text("キャンセル")
                }
            },
        )
    }

    // 全削除ダイアログ
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("全てのレポートを削除") },
            text = { Text("全てのクラッシュレポートを削除してもよろしいですか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        CrashReporter.deleteAll(context)
                        refresh()
                        showDeleteAllDialog = false
                    },
                ) {
                    Text("全て削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReportItem(
    report: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val date = Date(report.lastModified())
    val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    ListItem(
        headlineContent = { Text(report.name) },
        supportingContent = { Text(format.format(date)) },
        modifier =
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}
