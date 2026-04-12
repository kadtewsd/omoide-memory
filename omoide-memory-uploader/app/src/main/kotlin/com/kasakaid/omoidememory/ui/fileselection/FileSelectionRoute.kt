package com.kasakaid.omoidememory.ui.fileselection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun FileSelectionRoute(
    viewModel: FileSelectionViewModel = hiltViewModel(),
    initialMode: SelectionMode = SelectionMode.TARGET,
    toMainScreen: () -> Unit,
) {
    LaunchedEffect(initialMode) {
        viewModel.initMode(initialMode)
    }
    val selectionMode by viewModel.selectionMode.collectAsState()
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

    if (initialMode == SelectionMode.DONE || selectionMode == SelectionMode.DONE) {
        UploadedListScreen(
            pendingFiles = pendingFiles,
            selectedIds = viewModel.selectedIds,
            doneFilter = viewModel.doneFilter.collectAsState().value,
            onDoneFilterChanged = { viewModel.setDoneFilter(it) },
            onDeleteFromDrive = { viewModel.deleteFromDrive(it) },
            onToggle = { viewModel.toggleSelection(it) },
            toMainScreen = toMainScreen,
            onOff = onOff,
            onSwitchChanged = { viewModel.toggleAll(it) },
            isUploading = isUploading,
            progress = progress,
            onCancelUpload = { viewModel.cancelManualUpload() },
            isDeleting = isDeleting,
            deleteProgress = deleteProgress,
            onCancelDelete = { viewModel.cancelDelete() },
        )
    } else {
        UploadSelectionScreen(
            pendingFiles = pendingFiles,
            selectedIds = viewModel.selectedIds,
            selectionMode = selectionMode,
            onSelectionModeChanged = { viewModel.setSelectionMode(it) },
            onContentFixed = { viewModel.startManualUpload(it) },
            onRevive = { viewModel.revive(it) },
            onToggle = { viewModel.toggleSelection(it) },
            toMainScreen = toMainScreen,
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
}
