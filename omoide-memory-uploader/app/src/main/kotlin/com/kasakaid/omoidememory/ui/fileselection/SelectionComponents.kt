package com.kasakaid.omoidememory.ui.fileselection

import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.ui.AppBarWithBackIcon
import com.kasakaid.omoidememory.ui.MySwitch
import com.kasakaid.omoidememory.ui.OnOff
import com.kasakaid.omoidememory.ui.UploadIndicator

@Composable
fun BaseFileSelectionScreen(
    title: String,
    onBack: () -> Unit,
    subHeader: @Composable ColumnScope.() -> Unit,
    bottomBarAction: @Composable ColumnScope.(selectedFiles: List<OmoideMemory>) -> Unit,
    pendingFiles: List<OmoideMemory>,
    selectedIds: Map<Long, Boolean>,
    onToggle: (Long) -> Unit,
    isSelectable: (OmoideMemory) -> Boolean,
    onOff: OnOff,
    onSwitchChanged: (OnOff) -> Unit,
    isUploading: Boolean,
    progress: Pair<Int, Int>?,
    onCancelUpload: () -> Unit,
    isDeleting: Boolean,
    deleteProgress: Pair<Int, Int>?,
    onCancelDelete: () -> Unit,
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
        bottomBar = {
            val selectedFiles = pendingFiles.filter { selectedIds[it.id] == true }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                bottomBarAction(selectedFiles)
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            subHeader()
            MySwitch(
                onOff = onOff,
                onSwitchChanged = onSwitchChanged,
            )

            Spacer(Modifier.size(1.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(
                    items = pendingFiles,
                    key = { it.id },
                ) { item ->
                    FileItemCard(
                        item = item,
                        imageLoader = imageLoader,
                        isSelected = selectedIds[item.id] ?: false,
                        isSelectable = isSelectable(item),
                        onToggle = { onToggle(item.id) },
                        onPreview = { previewingItem = item },
                    )
                }
            }
        }
    }
    if (isUploading) {
        UploadIndicator(
            uploadProgress = progress,
            onCancel = onCancelUpload,
        )
    }

    if (isDeleting) {
        UploadIndicator(
            uploadProgress = deleteProgress,
            label = "削除中...",
            onCancel = onCancelDelete,
        )
    }
}

@Composable
fun SelectionModeRow(
    selectionMode: SelectionMode,
    onSelectionModeChanged: (SelectionMode) -> Unit,
    filterDone: Boolean,
) {
    androidx.compose.foundation.layout.Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(4.dp),
    ) {
        Text(
            text = "アップロード状態:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp),
        )
        if (!filterDone) {
            Text(
                text = SelectionMode.DONE.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        } else {
            SelectionMode.entries.filter { it != SelectionMode.DONE }.forEach { mode ->
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectionMode == mode,
                        onClick = { onSelectionModeChanged(mode) },
                    )
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { onSelectionModeChanged(mode) },
                    )
                }
            }
        }
    }
}

@Composable
fun DoneFilterRow(
    doneFilter: DoneFilter,
    onDoneFilterChanged: (DoneFilter) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(4.dp),
    ) {
        Text(
            text = "フィルタ:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp),
        )
        DoneFilter.entries.forEach { f ->
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = doneFilter == f,
                    onClick = { onDoneFilterChanged(f) },
                )
                Text(
                    text = f.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { onDoneFilterChanged(f) },
                )
            }
        }
    }
}

fun Context.imageLoader(): ImageLoader =
    ImageLoader
        .Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
            add(VideoFrameDecoder.Factory())
        }.build()

fun formatSize(bytes: Long): String {
    val mb = 1024 * 1024L
    val gb = 1024 * 1024 * 1024L

    return if (bytes >= gb) {
        "%.2f GB".format(bytes.toDouble() / gb)
    } else {
        "%.2f MB".format(bytes.toDouble() / mb)
    }
}
