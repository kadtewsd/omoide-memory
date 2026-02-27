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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.work.WorkManager
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.extension.WorkManagerExtension.enqueueWManualUpload
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManual
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeUploadingStateByManualTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.set

@HiltViewModel
class FileSelectionViewModel
    @Inject
    constructor(
        private val localFileRepository: OmoideMemoryRepository,
        private val application: Application,
    ) : ViewModel() {
        /**
         * 下記の挙動であるため、画面上に「なにも表示されない」時間がない。scan を使うことで UX を向上 (改善の余地はある)
         * scan を使ったリスト構築の挙動
         * 現在のコードだと、以下のような挙動になります：
         * fileA が届く → [fileA] を流す
         * fileB が届く → [fileA, fileB] を流す
         * fileC が届く → [fileA, fileB, fileC] を流す
         *
         * メリット: 画面（LazyColumnなど）に、ファイルが一つずつ「ポポポッ」と追加されていくような、視覚的に面白い動きになります。
         * デメリット: * 100件ある場合、UI は 100 回更新されます。また、途中の「未完成のリスト」を UI が受け取ることになります。
         */
        val pendingFiles: StateFlow<List<OmoideMemory>> =
            localFileRepository
                .getPotentialPendingFiles()
                .onEach { file ->
                    // 🚀 データが流れてきたタイミングで、まだ選択状態が空なら全選択にする
                    selectedIds[file.id] = _onOff.value.isChecked
                }.scan(emptyList<OmoideMemory>()) { acc, value -> acc + value } // リストに成長させる
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        // 選択されたハッシュを管理する Set
        val selectedIds = mutableStateMapOf<Long, Boolean>()

        fun toggleSelection(id: Long) {
            selectedIds[id] = !(selectedIds[id] ?: false)
        }

        private val _onOff: MutableStateFlow<OnOff> = MutableStateFlow(OnOff.On)
        val onOff: StateFlow<OnOff> = _onOff.asStateFlow()

        /**
         *  すべてのコンテンツを反転
         */
        fun toggleAll(onOff: OnOff) {
            _onOff.value = onOff
            selectedIds.forEach { (hash, _) ->
                selectedIds[hash] = onOff.isChecked
            }
        }

        private val workManager = WorkManager.getInstance(application)
        val isUploading: StateFlow<Boolean> =
            workManager.observeUploadingStateByManualTag(
                viewModelScope = viewModelScope,
            )
        val progress: StateFlow<Pair<Int, Int>?> =
            workManager.observeProgressByManual(
                viewModelScope = viewModelScope,
            )

        fun startManualUpload(ids: List<Long>) {
            viewModelScope.launch {
                val targets =
                    pendingFiles.value
                        .filter { it.id in ids }
                        .map {
                            it.apply { state = UploadState.READY }
                        }
                localFileRepository.markAsReady(targets)
                workManager.enqueueWManualUpload()
            }
        }
    }

@Composable
fun FileSelectionRoute(
    viewModel: FileSelectionViewModel = hiltViewModel(),
    toMainScreen: () -> Unit,
) {
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val onOff by viewModel.onOff.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    var hasStartedUploading by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(isUploading) {
        /**
         * 手動でアップロードが完了していたら元の画面に遷移させます。
         * Ux 的には戻らない方が良いですが、実装のシンプルさを優先
         */
        if (!isUploading && hasStartedUploading) {
            toMainScreen()
        }
        if (isUploading) {
            hasStartedUploading = true
        }
    }

    FileSelectionScreen(
        selectedIds = viewModel.selectedIds,
        pendingFiles = pendingFiles,
        onContentFixed = { ids ->
            // 🚀 ここで Worker をキック
            viewModel.startManualUpload(ids)
        },
        onToggle = { id ->
            viewModel.toggleSelection(id)
        },
        toMainScreen = toMainScreen,
        onOff = onOff,
        onSwitchChanged = { onOff ->
            viewModel.toggleAll(onOff)
        },
        isUploading = isUploading,
        progress = progress,
    )
}

@Composable
fun FileSelectionScreen(
    selectedIds: Map<Long, Boolean>,
    pendingFiles: List<OmoideMemory>,
    onContentFixed: (fileIds: List<Long>) -> Unit,
    onToggle: (hash: Long) -> Unit,
    toMainScreen: () -> Unit,
    onOff: OnOff,
    onSwitchChanged: (OnOff) -> Unit,
    isUploading: Boolean,
    progress: Pair<Int, Int>?,
) {
    Scaffold(
        topBar = { AppBarWithBackIcon(toMainScreen) },
        bottomBar = {
            val selectedFiles = pendingFiles.filter { selectedIds[it.id] == true }
            val totalSize = selectedFiles.sumOf { it.fileSize ?: 0L }
            val limit = 10 * 1024 * 1024 * 1024L
            val isOverLimit = totalSize > limit
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isOverLimit) {
                    Text(
                        text = "10GB を超えるアップロードはできません",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Button(
                    onClick = {
                        onContentFixed(selectedFiles.map { it.id })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading && selectedFiles.isNotEmpty() && !isOverLimit,
                ) {
                    Text("${selectedFiles.size} 件 (${formatSize(totalSize)}) をアップロード")
                }
            }
        },
    ) { innerPadding ->
        // 🚀 Scaffold が「ここがコンテンツの表示可能領域だよ」と教えてくれている
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            MySwitch(
                onOff = onOff,
                onSwitchChanged,
            )

            Spacer(Modifier.size(1.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // グリッド内の余白も Scaffold に合わせるならここでも padding を使う
                        .padding(8.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(
                    items = pendingFiles,
                    key = { it.id },
                ) { item ->
                    FileItemCard(
                        item = item,
                        isSelected = selectedIds[item.id] ?: false,
                        onToggle = { onToggle(item.id) },
                    )
                }
            }
        }
    }
    // 🚀 アップロード中のみ表示されるロック層
    if (isUploading) {
        UploadIndicator(
            uploadProgress = progress,
        )
    }
}

fun Context.imageLoader(): ImageLoader {
    // Activity や Application クラス、または DI モジュールで設定
    return ImageLoader
        .Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
            // 🚀 これが動画サムネイルの正体！
            add(VideoFrameDecoder.Factory())
        }.build()
}

@Composable
fun FileItemCard(
    item: OmoideMemory,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    // 選択状態に応じた色の定義
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderStroke = if (isSelected) 3.dp else 0.dp

    // Box で AsyncImage と CheckBox を重ねる
    Box(
        modifier =
            Modifier
                .padding(4.dp)
                .aspectRatio(1f) // Box自体を正方形に
                .border(borderStroke, borderColor, RoundedCornerShape(8.dp)) // 枠線を追加
                .clip(RoundedCornerShape(8.dp))
                .clickable { onToggle() }, // clip の後に clickable を書くのがコツ
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(item.filePath)
                    .videoFrameMillis(1000) // 🚀 1秒目のフレームを指定 (画像の場合は関係ないようよしなに Coil がやってくれる)
                    .crossfade(true) // じわっと表示させる（非同期感が出る）
                    .build(),
            imageLoader = LocalContext.current.imageLoader(),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .alpha(if (isSelected) 1f else 0.8f),
            // 選択時に少し強めに暗くする
            contentScale = ContentScale.Crop,
        )

        // チェックボックスも Material3 らしい配置に
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            // チェックボックスはトップに吸い寄せられてコンテンツの上側に描画
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/**
 * バイト数を見やすく表示
 */
private fun formatSize(bytes: Long): String {
    val mb = 1024 * 1024L
    val gb = 1024 * 1024 * 1024L

    return if (bytes >= gb) {
        "%.2f GB".format(bytes.toDouble() / gb)
    } else {
        "%.2f MB".format(bytes.toDouble() / mb)
    }
}
