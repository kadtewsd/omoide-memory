package com.kasakaid.omoidememory.ui.fileselection

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.UploadState

@Composable
fun FileSelectionRoute(
    viewModel: FileSelectionViewModel = hiltViewModel(),
    title: String,
    selectionMode: SelectionMode,
    subHeader: @Composable ColumnScope.() -> Unit = {},
    bottomBarAction: @Composable (selectedFiles: List<OmoideMemory>) -> Unit,
    toMainScreen: () -> Unit,
) {
    LaunchedEffect(selectionMode) {
        viewModel.initMode(selectionMode)
    }
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val onOff by viewModel.onOff.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val deleteProgress by viewModel.deleteProgress.collectAsState()

    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                viewModel.deleteAfterPermission()
            }
        }

    LaunchedEffect(Unit) {
        viewModel.deleteRequestEvent.collect { pendingIntent ->
            launcher.launch(
                androidx.activity.result.IntentSenderRequest
                    .Builder(pendingIntent.intentSender)
                    .build(),
            )
        }
    }

    var hasStartedUploading by remember { mutableStateOf(false) }
    LaunchedEffect(isUploading) {
        if (!isUploading && hasStartedUploading) {
            toMainScreen()
        }
        if (isUploading) {
            hasStartedUploading = true
        }
    }

    FileSelectionScreen(
        title = title,
        onBack = toMainScreen,
        subHeader = subHeader,
        bottomBarAction = bottomBarAction,
        pendingFiles = pendingFiles,
        selectedIds = viewModel.selectedIds,
        onToggle = { viewModel.toggleSelection(it) },
        isSelectable = { selectionMode != SelectionMode.DONE || it.state == UploadState.DONE },
        onOff = onOff,
        onSwitchChanged = { viewModel.toggleAll(it) },
        isUploading = isUploading,
        progress = progress,
        onCancelUpload = { viewModel.cancelManualUpload() },
        isDeleting = isDeleting,
        deleteProgress = deleteProgress,
        onCancelDelete = { viewModel.cancelDelete() },
    )
}
