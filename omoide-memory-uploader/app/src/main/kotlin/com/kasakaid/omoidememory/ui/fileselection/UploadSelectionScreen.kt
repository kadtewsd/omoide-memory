package com.kasakaid.omoidememory.ui.fileselection

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.isOverLimit
import com.kasakaid.omoidememory.data.totalSize
import com.kasakaid.omoidememory.ui.OnOff

@Composable
fun UploadSelectionScreen(
    pendingFiles: List<OmoideMemory>,
    selectedIds: Map<Long, Boolean>,
    selectionMode: SelectionMode,
    onSelectionModeChanged: (SelectionMode) -> Unit,
    onContentFixed: (List<Long>) -> Unit,
    onRevive: (List<Long>) -> Unit,
    onToggle: (Long) -> Unit,
    toMainScreen: () -> Unit,
    onOff: OnOff,
    onSwitchChanged: (OnOff) -> Unit,
    isUploading: Boolean,
    progress: Pair<Int, Int>?,
    onCancelUpload: () -> Unit,
    isDeleting: Boolean,
    deleteProgress: Pair<Int, Int>?,
    onCancelDelete: () -> Unit,
) {
    BaseFileSelectionScreen(
        title = "アップロードする写真を選択",
        onBack = toMainScreen,
        subHeader = {
            SelectionModeRow(
                selectionMode = selectionMode,
                onSelectionModeChanged = onSelectionModeChanged,
                filterDone = true,
            )
        },
        bottomBarAction = { selectedFiles ->
            val isOverLimit = selectedFiles.isOverLimit()
            val totalSize = selectedFiles.totalSize()

            if (selectionMode == SelectionMode.TARGET && isOverLimit) {
                Text(
                    text = "10GB を超えるアップロードはできません",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Text(
                    text = "選択中: ${selectedFiles.size} 件 (${formatSize(totalSize)})",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Button(
                onClick = {
                    val ids = selectedFiles.map { it.id }
                    if (selectionMode == SelectionMode.TARGET) onContentFixed(ids) else onRevive(ids)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    !isUploading && !isDeleting && selectedFiles.isNotEmpty() &&
                        (selectionMode != SelectionMode.TARGET || !isOverLimit),
            ) {
                Text(if (selectionMode == SelectionMode.TARGET) "送信" else "復活")
            }
        },
        pendingFiles = pendingFiles,
        selectedIds = selectedIds,
        onToggle = onToggle,
        isSelectable = { true },
        onOff = onOff,
        onSwitchChanged = onSwitchChanged,
        isUploading = isUploading,
        progress = progress,
        onCancelUpload = onCancelUpload,
        isDeleting = isDeleting,
        deleteProgress = deleteProgress,
        onCancelDelete = onCancelDelete,
    )
}
