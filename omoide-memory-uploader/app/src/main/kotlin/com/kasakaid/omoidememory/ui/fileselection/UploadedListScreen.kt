package com.kasakaid.omoidememory.ui.fileselection

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.ui.OnOff

@Composable
fun UploadedListScreen(
    pendingFiles: List<OmoideMemory>,
    selectedIds: Map<Long, Boolean>,
    doneFilter: DoneFilter,
    onDoneFilterChanged: (DoneFilter) -> Unit,
    onDeleteFromDrive: (List<Long>) -> Unit,
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
        title = "アップロード済みの写真",
        onBack = toMainScreen,
        subHeader = {
            SelectionModeRow(
                selectionMode = SelectionMode.DONE,
                onSelectionModeChanged = {},
                filterDone = false,
            )
            DoneFilterRow(
                doneFilter = doneFilter,
                onDoneFilterChanged = onDoneFilterChanged,
            )
        },
        bottomBarAction = { selectedFiles ->
            if (doneFilter != DoneFilter.DELETED) {
                Button(
                    onClick = {
                        onDeleteFromDrive(selectedFiles.filter { it.state == UploadState.DONE }.map { it.id })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading && !isDeleting && selectedFiles.any { it.state == UploadState.DONE },
                ) {
                    Text("ドライブから削除")
                }
            }
        },
        pendingFiles = pendingFiles,
        selectedIds = selectedIds,
        onToggle = onToggle,
        isSelectable = { it.state == UploadState.DONE },
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
