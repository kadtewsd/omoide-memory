package com.kasakaid.omoidememory.ui.fileselection

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.data.isOverLimit

@Composable
fun FileSelectionRoute(
    viewModel: FileSelectionViewModel = hiltViewModel(),
    title: String,
    fileUploadState: FileUploadState,
    subHeader: @Composable ColumnScope.() -> Unit = {},
    bottomBarAction: @Composable (selectedFiles: List<OmoideMemory>) -> Unit,
    toMainScreen: (List<Long>) -> Unit,
) {
    LaunchedEffect(fileUploadState) {
        viewModel.initMode(fileUploadState)
    }
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val onOff by viewModel.onOff.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val deleteProgress by viewModel.deleteProgress.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

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

    LaunchedEffect(Unit) {
        viewModel.deleteResultEvent.collect { notDeletedIds ->
            viewModel.clearSelection()
            toMainScreen(notDeletedIds)
        }
    }

    var hasStartedProcessing by remember { mutableStateOf(false) }
    LaunchedEffect(isProcessing) {
        if (!isProcessing && hasStartedProcessing && fileUploadState != FileUploadState.UPLOAD_DONE) {
            // サーバーサイドの重たい処理が終わったのでコールバック的に画面の選択状態を解除
            viewModel.clearSelection()
            toMainScreen(emptyList())
        }
        if (isProcessing) {
            hasStartedProcessing = true
        }
    }

    FileSelectionScreen(
        title = title,
        onBack = { toMainScreen(emptyList()) },
        subHeader = subHeader,
        bottomBarAction = bottomBarAction,
        pendingFiles = pendingFiles,
        selectedIds = viewModel.selectedIds,
        onToggle = { viewModel.toggleSelection(it) },
        isSelectable = { fileUploadState != FileUploadState.UPLOAD_DONE || it.state == UploadState.DONE },
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

/**
 * 除外リスト画面のファサードコンポーネント。
 * 除外されたファイルを一覧表示し、復活させることができる。
 * AppRouter の記述をシンプルに保つため、内部でサブヘッダーやアクション行のロジックを保持する。
 *
 * @param title 画面タイトル
 * @param onBack 戻るボタン押下時のコールバック
 * @param onNavigateToTarget アップロード対象選択画面への遷移コールバック
 */
@Composable
fun ExcludedFileSelectionRoute(
    title: String,
    onBack: () -> Unit,
    onNavigateToTarget: () -> Unit,
    viewModel: FileSelectionViewModel = hiltViewModel(),
) {
    val isProcessing by viewModel.isProcessing.collectAsState()

    FileSelectionRoute(
        viewModel = viewModel,
        title = title,
        fileUploadState = FileUploadState.UPLOAD_EXCLUDED,
        subHeader = {
            SelectionModeRow(
                fileUploadState = FileUploadState.UPLOAD_EXCLUDED,
                onSelectionModeChanged = { mode ->
                    if (mode == FileUploadState.WAITING_FOR_UPLOAD) {
                        onNavigateToTarget()
                    }
                },
                filterDone = true,
            )
        },
        bottomBarAction = { selectedFiles ->
            StandardFileSelection(selectedFiles = selectedFiles) {
                Button(
                    onClick = { viewModel.revive(selectedFiles.map { it.id }) },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && selectedFiles.isNotEmpty(),
                ) {
                    Text("復活")
                }
            }
        },
        toMainScreen = { onBack() },
    )
}

/**
 * アップロード済みファイルのメンテナンス画面のファサードコンポーネント。
 * アップロード済みのファイルを一覧表示し、ドライブからの削除などを行うことができる。
 * AppRouter の記述をシンプルに保つため、内部でサブヘッダーやアクション行のロジックを保持する。
 *
 * @param title 画面タイトル
 * @param onBack 戻るボタン押下時のコールバック
 */
@Composable
fun DoneFileSelectionRoute(
    title: String,
    onBack: (List<Long>) -> Unit,
    viewModel: FileSelectionViewModel = hiltViewModel(),
) {
    val isProcessing by viewModel.isProcessing.collectAsState()
    val doneFilter by viewModel.doneFilter.collectAsState()

    FileSelectionRoute(
        viewModel = viewModel,
        title = title,
        fileUploadState = FileUploadState.UPLOAD_DONE,
        subHeader = {
            SelectionModeRow(
                fileUploadState = FileUploadState.UPLOAD_DONE,
                onSelectionModeChanged = {},
                filterDone = false,
            )
            DoneFilterRow(
                doneFilter = doneFilter,
                onDoneFilterChanged = { viewModel.setDoneFilter(it) },
            )
        },
        bottomBarAction = { selectedFiles ->
            StandardFileSelection(selectedFiles = selectedFiles) {
                if (doneFilter != DoneFilter.DELETED) {
                    Button(
                        onClick = {
                            viewModel.deleteFromDrive(
                                selectedFiles.filter { it.state == UploadState.DONE }.map { it.id },
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled =
                            !isProcessing &&
                                selectedFiles.any { it.state == UploadState.DONE },
                    ) {
                        Text("ドライブから削除")
                    }
                }
            }
        },
        toMainScreen = onBack,
    )
}

/**
 * 上限チェック機能付きのファイル選択画面のファサードコンポーネント。
 * 新規アップロード対象の選択を目的とする。
 * AppRouter の記述をシンプルに保つため、内部でサブヘッダーやアクション行のロジックを保持する。
 *
 * @param title 画面タイトル
 * @param onBack 戻るボタン押下時のコールバック
 * @param onNavigateToExcluded 除外リスト画面への遷移コールバック
 */
@Composable
fun LimitFileSelectionRoute(
    title: String,
    onBack: () -> Unit,
    onNavigateToExcluded: () -> Unit,
    viewModel: FileSelectionViewModel = hiltViewModel(),
) {
    val isProcessing by viewModel.isProcessing.collectAsState()

    FileSelectionRoute(
        viewModel = viewModel,
        title = title,
        fileUploadState = FileUploadState.WAITING_FOR_UPLOAD,
        subHeader = {
            SelectionModeRow(
                fileUploadState = FileUploadState.WAITING_FOR_UPLOAD,
                onSelectionModeChanged = { mode ->
                    if (mode == FileUploadState.UPLOAD_EXCLUDED) {
                        onNavigateToExcluded()
                    }
                },
                filterDone = true,
            )
        },
        bottomBarAction = { selectedFiles ->
            LimitFileSelection(selectedFiles = selectedFiles) {
                Button(
                    onClick = { viewModel.startManualUpload(selectedFiles.map { it.id }) },
                    modifier = Modifier.weight(1f),
                    enabled =
                        !isProcessing &&
                            selectedFiles.isNotEmpty() && !selectedFiles.isOverLimit(),
                ) {
                    Text("送信")
                }
                Button(
                    onClick = { viewModel.markAsRemoved(selectedFiles.map { it.id }) },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && selectedFiles.isNotEmpty(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                ) {
                    Text("除外")
                }
            }
        },
        toMainScreen = { onBack() },
    )
}
