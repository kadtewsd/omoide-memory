package com.kasakaid.omoidememory.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil.compose.AsyncImage
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.worker.GdriveUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.collections.set
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class FileSelectionViewModel @Inject constructor(
    omoideMemoryRepository: OmoideMemoryRepository,
) : ViewModel() {
    val pendingFiles: StateFlow<List<OmoideMemory>> = omoideMemoryRepository
        .getActualPendingFiles()
        .onEach { file ->
            // 🚀 データが流れてきたタイミングで、まだ選択状態が空なら全選択にする
            selectedHashes[file.hash] = true
        }
        .scan(emptyList<OmoideMemory>()) { acc, value -> acc + value } // リストに成長させる
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 選択されたハッシュを管理する Set
    val selectedHashes = mutableStateMapOf<String, Boolean>()

    fun toggleSelection(hash: String) {
        selectedHashes[hash] = !(selectedHashes[hash] ?: false)
    }
}

@Composable
fun FileSelectionRoute(
    viewModel: FileSelectionViewModel = hiltViewModel(),
    onFinished: () -> Unit,
) {
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val context = LocalContext.current

    FileSelectionScreen(
        selectedHashes = viewModel.selectedHashes,
        pendingFiles = pendingFiles,
        onContentFixed = { hashes ->
            // 🚀 ここで Worker をキック
            val uploadRequest = OneTimeWorkRequestBuilder<GdriveUploadWorker>()
                .setInputData(workDataOf("TARGET_HASHES" to hashes))
                .setInputData(workDataOf("TOTAL_COUNT" to viewModel.selectedHashes.count { it.value }))
                .addTag(GdriveUploadWorker.TAG)
                .build()

            WorkManager.getInstance(context).enqueue(uploadRequest)
        },
        onToggle = { hash ->
            viewModel.selectedHashes[hash] = !(viewModel.selectedHashes[hash] ?: false)
        },
        onFinished = onFinished,
    )
}

@Composable
fun FileSelectionScreen(
    selectedHashes: Map<String, Boolean>,
    pendingFiles: List<OmoideMemory>,
    onContentFixed: (hashes: Array<String>) -> Unit,
    onToggle: (hash: String) -> Unit,
    onFinished: () -> Unit,
) {

    Scaffold(
        topBar = { AppBarWithBackIcon(onFinished) },
        bottomBar = {
            Button(
                onClick = {
                    val hashes = selectedHashes.filter { it.value }.keys.toTypedArray()
                    onContentFixed(hashes)
                    onFinished() // 遷移元（ホーム）に戻る
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = selectedHashes.values.any { it }
            ) {
                Text("${selectedHashes.values.count { it }} 件をアップロード")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // Scaffold が計算した上下の隙間をセット
                    .padding(innerPadding),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(
                    items = pendingFiles,
                    key = { it.hash }
                ) { item ->
                    FileItemCard(
                        item = item,
                        isSelected = selectedHashes[item.hash] ?: false,
                        onToggle = { onToggle(item.hash) },
                    )
                }
            }
        }
    }
}

fun Context.imageLoader(): ImageLoader {

    // Activity や Application クラス、または DI モジュールで設定
    return ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
            // 🚀 これが動画サムネイルの正体！
            add(VideoFrameDecoder.Factory())
        }
        .build()
}

@Composable
fun FileItemCard(item: OmoideMemory, isSelected: Boolean, onToggle: () -> Unit) {
    // 選択状態に応じた色の定義
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderStroke = if (isSelected) 3.dp else 0.dp

    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f) // Box自体を正方形に
            .border(borderStroke, borderColor, RoundedCornerShape(8.dp)) // 枠線を追加
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() } // clip の後に clickable を書くのがコツ
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.filePath)
                .videoFrameMillis(1000) // 🚀 1秒目のフレームを指定 (画像の場合は関係ないようよしなに Coil がやってくれる)
                .crossfade(true) // じわっと表示させる（非同期感が出る）
                .build(),
            imageLoader = LocalContext.current.imageLoader(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isSelected) 1f else 1.5f), // 選択時に少し強めに暗くする
            contentScale = ContentScale.Crop
        )

        // チェックボックスも Material3 らしい配置に
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}