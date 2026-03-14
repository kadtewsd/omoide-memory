package com.kasakaid.omoidememory.ui.maintenance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.ui.AppRowBarWithBackIcon
import com.kasakaid.omoidememory.ui.EnumDropdown

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DbMaintenanceScreen(
    onBack: () -> Unit,
    viewModel: DbMaintenanceViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val filterState by viewModel.filterState.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppRowBarWithBackIcon(
                title = "DB メンテナンス",
                onFinished = onBack,
                actions = {
                    IconButton(onClick = { viewModel.reload() }, enabled = !isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "更新")
                    }
                },
            )
        },
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${selectedIds.size} 件選択中",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        // State 更新ボタン
                        EnumDropdown(
                            label = "state更新",
                            items = UploadState.entries.toTypedArray(),
                            selectedItem = null,
                            onItemSelected = { it?.let { viewModel.updateState(it) } },
                            trigger = { onClick, _ ->
                                Button(
                                    onClick = onClick,
                                    modifier = Modifier.height(40.dp),
                                ) {
                                    Text("state更新", fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                        )

                        // 削除ボタン
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.height(40.dp),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("削除", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            // ヘッダー操作
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // フィルター（スピナー風）
                EnumDropdown(
                    label = "すべて (全表示)",
                    items = UploadState.entries.toTypedArray(),
                    selectedItem = filterState,
                    onItemSelected = { viewModel.setFilterState(it) },
                    allLabel = "すべて (全表示)",
                )

                Spacer(Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { viewModel.selectAll() }, modifier = Modifier.height(32.dp)) {
                        Text("全選択", fontSize = 12.sp)
                    }
                    TextButton(onClick = { viewModel.clearSelection() }, modifier = Modifier.height(32.dp)) {
                        Text("全解除", fontSize = 12.sp)
                    }
                }
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Spacer(Modifier.weight(1f))
                Text("件数: ${rows.size}", style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider()

            if (rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("データがありません")
                }
            } else {
                // テーブルライクな一覧
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // ヘッダー行
                    stickyHeader {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.width(48.dp)) // チェックボックス分
                            Text(
                                "ID",
                                modifier = Modifier.width(80.dp),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                "State",
                                modifier = Modifier.width(100.dp),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                "Name / Path",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        HorizontalDivider()
                    }

                    items(rows, key = { it.id }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleSelect(item.id) }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleSelect(item.id) },
                                modifier = Modifier.width(48.dp),
                            )
                            Text(
                                item.id.toString(),
                                modifier = Modifier.width(80.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            StateLabel(
                                state = item.state,
                                modifier = Modifier.width(100.dp),
                            )
                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                            ) {
                                Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                item.filePath?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
                    }
                }
            }
        }
    }

    // 削除確認ダイアログ
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("削除の確認") },
            text = { Text("選択した ${selectedIds.size} 件をデータベースから削除しますか？\n(物理ファイルは削除されません)") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteConfirm = false
                    },
                ) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@Composable
private fun StateLabel(
    state: UploadState,
    modifier: Modifier = Modifier,
) {
    val color =
        when (state) {
            UploadState.READY -> Color(0xFF2196F3)

            // Blue
            UploadState.UPLOADING -> Color(0xFFFF9800)

            // Orange
            UploadState.DONE -> Color(0xFF4CAF50)

            // Green
            UploadState.FAILED -> Color(0xFFF44336)

            // Red
            UploadState.EXCLUDED -> Color(0xFF9E9E9E) // Gray
        }

    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.padding(end = 8.dp),
    ) {
        Text(
            text = state.name,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
