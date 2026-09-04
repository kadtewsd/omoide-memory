package com.kasakaid.omoidememory.ui.fileselection.upoadtriggered

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.ui.AppBarWithBackIcon
import com.kasakaid.omoidememory.ui.fileselection.FileGrid
import com.kasakaid.omoidememory.ui.fileselection.VideoPreviewDialog
import com.kasakaid.omoidememory.ui.fileselection.imageLoader
import com.kasakaid.omoidememory.ui.indicator.CONTENTS_UPLOADING
import com.kasakaid.omoidememory.ui.indicator.Progress
import com.kasakaid.omoidememory.ui.indicator.UploadIndicator
import com.kasakaid.omoidememory.ui.indicator.current

@Composable
fun UploadTriggeredSelectionRoute(
    onBack: () -> Unit,
    viewModel: UploadTriggeredSelectionViewModel = hiltViewModel(),
) {
    val files by viewModel.triggeredFiles.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uploadResultEvent.collect { summary ->
            when {
                summary.errorMessage != null -> snackbarHostState.showSnackbar(summary.errorMessage)
                summary.pendingCount > 0 -> snackbarHostState.showSnackbar("${summary.pendingCount}件のエラーが発生したので再度アップロードしてください")
                else -> onBack()
            }
        }
    }

    UploadTriggeredSelectionScreen(
        title = "アップロード再開",
        onBack = onBack,
        files = files,
        isUploading = isUploading,
        progress = progress,
        snackbarHostState = snackbarHostState,
        onResumeUpload = { viewModel.resumeUpload() },
        onCancelUpload = { viewModel.cancelUpload() },
    )
}

@Composable
fun UploadTriggeredSelectionScreen(
    title: String,
    onBack: () -> Unit,
    files: List<OmoideMemory>,
    isUploading: Boolean,
    progress: Progress?,
    snackbarHostState: SnackbarHostState,
    onResumeUpload: () -> Unit,
    onCancelUpload: () -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { context.imageLoader() }
    var previewingItem by remember { mutableStateOf<OmoideMemory?>(null) }

    previewingItem?.let { item ->
        VideoPreviewDialog(
            item = item,
            onDismissRequest = { previewingItem = null },
        )
    }

    Scaffold(
        topBar = { AppBarWithBackIcon(title = title, onFinished = onBack) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = onResumeUpload,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading && files.isNotEmpty(),
                ) {
                    Text("アップロード再開")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            FileGrid(
                files = files,
                imageLoader = imageLoader,
                isSelectable = { false },
                onPreview = { previewingItem = it },
            )
        }
    }

    if (isUploading) {
        UploadIndicator(
            uploadProgress = progress.current(total = files.size),
            label = CONTENTS_UPLOADING,
            onCancel = onCancelUpload,
        )
    }
}
