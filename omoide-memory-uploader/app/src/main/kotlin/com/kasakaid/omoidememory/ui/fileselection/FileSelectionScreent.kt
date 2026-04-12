package com.kasakaid.omoidememory.ui.fileselection

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
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
import com.kasakaid.omoidememory.data.ExcludeOmoideRepository
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.data.isOverLimit
import com.kasakaid.omoidememory.data.totalSize
import com.kasakaid.omoidememory.extension.WorkManagerExtension.enqueueManualDelete
import com.kasakaid.omoidememory.extension.WorkManagerExtension.enqueueWManualUpload
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeDeletingStateByManualTag
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManual
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManualDelete
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeUploadingStateByManualTag
import com.kasakaid.omoidememory.network.GoogleDriveService
import com.kasakaid.omoidememory.ui.AppBarWithBackIcon
import com.kasakaid.omoidememory.ui.MySwitch
import com.kasakaid.omoidememory.ui.OnOff
import com.kasakaid.omoidememory.ui.UploadIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.set

enum class SelectionMode(
    val label: String,
) {
    TARGET("待ち"),
    EXCLUDED("除外"),
    DONE("完了"),
}

enum class DoneFilter(
    val label: String,
) {
    ALL("すべて"),
    NOT_DELETED("未削除"),
    DELETED("削除済み"),
}

@HiltViewModel
class FileSelectionViewModel
    @Inject
    constructor(
        private val localFileRepository: OmoideMemoryRepository,
        private val excludeOmoideRepository: ExcludeOmoideRepository,
        private val driveService: GoogleDriveService,
        application: Application,
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
        private val _selectionMode = MutableStateFlow(SelectionMode.TARGET)
        val selectionMode: StateFlow<SelectionMode> = _selectionMode.asStateFlow()

        fun setSelectionMode(mode: SelectionMode) {
            _selectionMode.value = mode
            selectedIds.clear()
        }

        fun initMode(mode: SelectionMode) {
            _selectionMode.value = mode
        }

        private val _doneFilter = MutableStateFlow(DoneFilter.ALL)
        val doneFilter: StateFlow<DoneFilter> = _doneFilter.asStateFlow()

        fun setDoneFilter(filter: DoneFilter) {
            _doneFilter.value = filter
            selectedIds.clear()
        }

        // 削除確認ダイアログ（OS側）を起動するためのイベント
        private val _deleteRequestEvent = MutableSharedFlow<android.app.PendingIntent>()
        val deleteRequestEvent: SharedFlow<android.app.PendingIntent> = _deleteRequestEvent.asSharedFlow()

        // 許可が得られた後に DB を消すための保持用
        private var pendingDeleteEntities: List<OmoideMemory> = emptyList()

        /**
         * [監視対象]
         * 1. 表示モード (selectionMode): ラジオボタンの切り替え
         * 2. DBの全レコード件数 (getAllUploadedIdsAsFlow): 除外(exclude)や復活(revive)による件数変化
         *
         * [振る舞い]
         * 上記いずれかに変化があれば、flatMapLatest によって表示対象の Flow を最新のものに差し替える。
         * 特に除外(exclude)や「復活」操作などで DB の件数が変わった際、この combine が発火することで
         * TARGET モードの getPotentialPendingFiles() が再スキャンを開始し、画面がリアクティブに更新される。
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        val pendingFiles: StateFlow<List<OmoideMemory>> =
            combine(selectionMode, doneFilter, localFileRepository.getAllUploadedIdsAsFlow()) { mode, filter, _ ->
                mode to filter
            }.flatMapLatest { (mode, filter) ->
                when (mode) {
                    SelectionMode.TARGET -> {
                        localFileRepository
                            .getPotentialPendingFiles()
                            .onEach { file ->
                                if (selectedIds[file.id] == null) {
                                    selectedIds[file.id] = _onOff.value.isChecked
                                }
                            }.scan(emptyList()) { acc, value -> acc + value }
                    }

                    SelectionMode.EXCLUDED -> {
                        localFileRepository.findByAsFlow(UploadState.EXCLUDED)
                    }

                    SelectionMode.DONE -> {
                        localFileRepository.findByAsFlow(listOf(UploadState.DONE, UploadState.DRIVE_DELETED)).combine(doneFilter) { files, f ->
                            when (f) {
                                DoneFilter.ALL -> files
                                DoneFilter.NOT_DELETED -> files.filter { it.state == UploadState.DONE }
                                DoneFilter.DELETED -> files.filter { it.state == UploadState.DRIVE_DELETED }
                            }
                        }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        // 選択されたハッシュを管理する Set
        val selectedIds = mutableStateMapOf<Long, Boolean>()

        fun toggleSelection(id: Long) {
            selectedIds[id] = !(selectedIds[id] ?: false)
        }

        private val _onOff: MutableStateFlow<OnOff> = MutableStateFlow(OnOff.Off)
        val onOff: StateFlow<OnOff> = _onOff.asStateFlow()

        /**
         *  すべてのコンテンツを反転
         */
        fun toggleAll(onOff: OnOff) {
            _onOff.value = onOff
            val selectableIds =
                if (selectionMode.value == SelectionMode.DONE) {
                    pendingFiles.value
                        .filter { it.state == UploadState.DONE }
                        .map { it.id }
                        .toSet()
                } else {
                    selectedIds.keys
                }
            selectedIds.forEach { (hash, _) ->
                if (hash in selectableIds) {
                    selectedIds[hash] = onOff.isChecked
                } else {
                    selectedIds[hash] = false
                }
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
        val isDeleting: StateFlow<Boolean> =
            workManager.observeDeletingStateByManualTag(
                viewModelScope = viewModelScope,
            )
        val deleteProgress: StateFlow<Pair<Int, Int>?> =
            workManager.observeProgressByManualDelete(
                viewModelScope = viewModelScope,
            )

        fun startManualUpload(ids: List<Long>) {
            viewModelScope.launch {
                val idSet = ids.toSet()
                val targets =
                    pendingFiles.value
                        .filter { it.id in idSet }
                        .map {
                            it.ready()
                        }
                if (targets.isNotEmpty()) {
                    localFileRepository.add(targets)
                    selectedIds.clear()
                    workManager.enqueueWManualUpload()
                }
            }
        }

        fun cancelManualUpload() {
            workManager.cancelUniqueWork("manual_upload")
        }

        fun markAsRemoved(ids: List<Long>) {
            viewModelScope.launch {
                val targets = pendingFiles.value.filter { it.id in ids }.map { it.exclude() }
                if (targets.isNotEmpty()) {
                    localFileRepository.add(targets)
                }
            }
        }

        fun revive(ids: List<Long>) {
            viewModelScope.launch {
                excludeOmoideRepository.revive(ids)
            }
        }

        fun deletePhysically(items: List<OmoideMemory>) {
            viewModelScope.launch {
                val pendingIntent = localFileRepository.deletePhysically(items)
                if (pendingIntent != null) {
                    // OS の確認ダイアログが必要
                    pendingDeleteEntities = items
                    _deleteRequestEvent.emit(pendingIntent)
                } else {
                    // 直接消せた（または古いOS）
                    selectedIds.clear()
                }
            }
        }

        /**
         * OS ダイアログで「許可」された後に呼ばれる
         */
        fun deleteAfterPermission() {
            viewModelScope.launch {
                localFileRepository.delete(pendingDeleteEntities.map { it.id })
                pendingDeleteEntities = emptyList()
                selectedIds.clear()
            }
        }

        fun deleteFromDrive(ids: List<Long>) {
            viewModelScope.launch {
                if (ids.isNotEmpty()) {
                    workManager.enqueueManualDelete(ids)
                    selectedIds.clear()
                }
            }
        }

        fun cancelDelete() {
            workManager.cancelUniqueWork("manual_delete")
        }
    }

@Composable
fun FileSelectionRoute(
    viewModel: FileSelectionViewModel = hiltViewModel(),
    initialMode: SelectionMode = SelectionMode.TARGET,
    toMainScreen: () -> Unit,
) {
    LaunchedEffect(initialMode) {
        viewModel.initMode(initialMode)
    }
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val onOff by viewModel.onOff.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val deleteProgress by viewModel.deleteProgress.collectAsState()
    var hasStartedUploading by remember {
        mutableStateOf(false)
    }

    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                // ユーザーが削除を許可した
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
        selectionMode = viewModel.selectionMode.collectAsState().value,
        doneFilter = viewModel.doneFilter.collectAsState().value,
        onSelectionModeChanged = { mode ->
            viewModel.setSelectionMode(mode)
        },
        onDoneFilterChanged = { filter ->
            viewModel.setDoneFilter(filter)
        },
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
        onCancelUpload = {
            viewModel.cancelManualUpload()
        },
        isUploading = isUploading,
        progress = progress,
        onRemove = { ids ->
            viewModel.markAsRemoved(ids)
        },
        onRevive = { ids ->
            viewModel.revive(ids)
        },
        onDeletePhysically = { items ->
            viewModel.deletePhysically(items)
        },
        onDeleteFromDrive = { ids ->
            viewModel.deleteFromDrive(ids)
        },
        isDeleting = isDeleting,
        deleteProgress = deleteProgress,
        onCancelDelete = { viewModel.cancelDelete() },
    )
}

@Composable
fun FileSelectionScreen(
    selectedIds: Map<Long, Boolean>,
    pendingFiles: List<OmoideMemory>,
    selectionMode: SelectionMode,
    doneFilter: DoneFilter,
    onSelectionModeChanged: (SelectionMode) -> Unit,
    onDoneFilterChanged: (DoneFilter) -> Unit,
    onContentFixed: (fileIds: List<Long>) -> Unit,
    onToggle: (hash: Long) -> Unit,
    toMainScreen: () -> Unit,
    onOff: OnOff,
    onSwitchChanged: (OnOff) -> Unit,
    isUploading: Boolean,
    progress: Pair<Int, Int>?,
    onRemove: (ids: List<Long>) -> Unit,
    onRevive: (ids: List<Long>) -> Unit,
    onCancelUpload: () -> Unit,
    onDeletePhysically: (items: List<OmoideMemory>) -> Unit,
    onDeleteFromDrive: (ids: List<Long>) -> Unit,
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
        topBar = {
            val title = if (selectionMode == SelectionMode.DONE) "アップロード済みの写真" else "アップロードする写真を選択"
            AppBarWithBackIcon(title = title, onFinished = toMainScreen)
        },
        bottomBar = {
            val selectedFiles = pendingFiles.filter { selectedIds[it.id] == true }
            val totalSize = selectedFiles.totalSize()
            val isOverLimit = selectedFiles.isOverLimit()
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 情報テキストの表示
                if (selectionMode == SelectionMode.TARGET && isOverLimit) {
                    Text(
                        text = "10GB を超えるアップロードはできません",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else if (selectionMode != SelectionMode.DONE) {
                    Text(
                        text = "選択中: ${selectedFiles.size} 件 (${formatSize(totalSize)})",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                if (!(selectionMode == SelectionMode.DONE && doneFilter == DoneFilter.DELETED)) {
                    // アクションボタン
                    Button(
                        onClick = {
                            when (selectionMode) {
                                SelectionMode.TARGET -> {
                                    onContentFixed(selectedFiles.map { it.id })
                                }

                                SelectionMode.EXCLUDED -> {
                                    onRevive(selectedFiles.map { it.id })
                                }

                                SelectionMode.DONE -> {
                                    onDeleteFromDrive(selectedFiles.filter { it.state == UploadState.DONE }.map { it.id })
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled =
                            !isUploading && !isDeleting && selectedFiles.isNotEmpty() &&
                                (selectionMode != SelectionMode.TARGET || !isOverLimit) &&
                                (selectionMode != SelectionMode.DONE || selectedFiles.any { it.state == UploadState.DONE }),
                    ) {
                        when (selectionMode) {
                            SelectionMode.TARGET -> Text("送信")
                            SelectionMode.EXCLUDED -> Text("復活")
                            SelectionMode.DONE -> Text("ドライブから削除")
                        }
                    }
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
            // ラジオボタンの追加
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp),
                )
                // DONE モードの時は、他のモードに切り替えさせない
                if (selectionMode == SelectionMode.DONE) {
                    Text(
                        text = SelectionMode.DONE.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                } else {
                    SelectionMode.entries.filter { it != SelectionMode.DONE }.forEach { mode ->
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
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

            // DONE モードの時にフィルタを表示する
            if (selectionMode == SelectionMode.DONE) {
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
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    DoneFilter.entries.forEach { f ->
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
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
                        imageLoader = imageLoader,
                        isSelected = selectedIds[item.id] ?: false,
                        isSelectable = selectionMode != SelectionMode.DONE || item.state == UploadState.DONE,
                        onToggle = { onToggle(item.id) },
                        onPreview = { previewingItem = item },
                    )
                }
            }
        }
    }
    // 🚀 アップロード中のみ表示されるロック層
    if (isUploading) {
        UploadIndicator(
            uploadProgress = progress,
            onCancel = onCancelUpload,
        )
    }

    // 🚀 削除中のみ表示されるロック層
    if (isDeleting) {
        UploadIndicator(
            uploadProgress = deleteProgress,
            label = "削除中...",
            onCancel = onCancelDelete,
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
