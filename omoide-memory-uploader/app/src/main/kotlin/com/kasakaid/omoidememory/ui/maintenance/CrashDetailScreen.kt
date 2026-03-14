package com.kasakaid.omoidememory.ui.maintenance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kasakaid.omoidememory.os.CrashReporter
import com.kasakaid.omoidememory.ui.AppRowBarWithBackIcon
import org.json.JSONObject
import java.io.File

@Composable
fun CrashDetailScreen(
    fileName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val file = remember(fileName) { File(context.filesDir, "crashes/$fileName") }

    if (!file.exists()) {
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }

    val jsonString = remember(file) { file.readText() }
    val stackTrace =
        remember(jsonString) {
            try {
                JSONObject(jsonString).getString("stackTrace")
            } catch (e: Exception) {
                "解析エラー: $jsonString"
            }
        }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppRowBarWithBackIcon(
                title = "レポート詳細",
                onFinished = onBack,
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("💥 スタック", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("📄 生JSON", modifier = Modifier.padding(16.dp))
                }
            }

            val scrollState = rememberScrollState()
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = if (selectedTab == 0) stackTrace else jsonString,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("レポートの削除") },
            text = { Text("このレポートを削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        CrashReporter.deleteReport(file)
                        showDeleteDialog = false
                        onBack()
                    },
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("キャンセル")
                }
            },
        )
    }
}
