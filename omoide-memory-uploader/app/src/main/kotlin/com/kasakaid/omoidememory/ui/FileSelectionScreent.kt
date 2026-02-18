package com.kasakaid.omoidememory.ui

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.extension.WorkManagerExtension.enqueueWManualUpload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.collections.set

@HiltViewModel
class FileSelectionViewModel @Inject constructor(
    omoideMemoryRepository: OmoideMemoryRepository,
    private val application: Application,
) : ViewModel() {
    val pendingFiles: StateFlow<List<OmoideMemory>> = omoideMemoryRepository
        .getActualPendingFiles()
        .onEach { file ->
            // 🚀 データが流れてきたタイミングで、まだ選択状態が空なら全選択にする
            selectedHashes[file.hash] = _onOff.value.isChecked
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

    val _onOff: MutableStateFlow<OnOff> = MutableStateFlow(OnOff.On)
    val onOff: StateFlow<OnOff> = _onOff.asStateFlow()

    /**
     *  すべてのコンテンツを反転
     */
    fun toggleAll(onOff: OnOff) {
        _onOff.value = onOff
        selectedHashes.forEach { (hash, _) ->
            selectedHashes[hash] = onOff.isChecked
        }
    }

    fun enqueueWManualUpload(
        hashes: Array<String>,
    ) {
        application.enqueueWManualUpload(
            hashes = hashes,
            totalCount = selectedHashes.count { it.value },
        )
    }
}

@Composable
fun FileSelectionRoute(
    viewModel: FileSelectionViewModel = hiltViewModel(),
    onFinished: () -> Unit,
) {
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val onOff by viewModel.onOff.collectAsState()

    FileSelectionScreen(
        selectedHashes = viewModel.selectedHashes,
        pendingFiles = pendingFiles,
        onContentFixed = { hashes ->
            // 🚀 ここで Worker をキック
            viewModel.enqueueWManualUpload(hashes)
        },
        onToggle = { hash ->
            viewModel.toggleSelection(hash)
        },
        onFinished = onFinished,
        onOff = onOff,
        onSwitchChanged = { onOff ->
            viewModel.toggleAll(onOff)
        }
    )
}

@Composable
fun FileSelectionScreen(
    selectedHashes: Map<String, Boolean>,
    pendingFiles: List<OmoideMemory>,
    onContentFixed: (hashes: Array<String>) -> Unit,
    onToggle: (hash: String) -> Unit,
    onFinished: () -> Unit,
    onOff: OnOff,
    onSwitchChanged: (OnOff) -> Unit,
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
    ) { innerPadding -> // 🚀 Scaffold が「ここがコンテンツの表示可能領域だよ」と教えてくれている
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            MySwitch(
                onOff = onOff,
                onSwitchChanged
            )

            Spacer(Modifier.size(1.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // グリッド内の余白も Scaffold に合わせるならここでも padding を使う
                    .padding(8.dp),
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

    // Box で AsyncImage と CheckBox を重ねる
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
                .alpha(if (isSelected) 1f else 0.8f), // 選択時に少し強めに暗くする
            contentScale = ContentScale.Crop
        )

        // チェックボックスも Material3 らしい配置に
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            // チェックボックスはトップに吸い寄せられてコンテンツの上側に描画
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}