package com.kasakaid.omoidememory.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.extension.WorkManagerExtension.enqueueWManualDelete
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeDeletingStateByManualTag
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManualDelete
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DriveFileDeleteViewModel
    @Inject
    constructor(
        omoideMemoryRepository: OmoideMemoryRepository,
        private val application: Application,
    ) : ViewModel() {
        val pendingFiles: StateFlow<List<OmoideMemory>> =
            omoideMemoryRepository
                .getUploadedFiles()
                .onEach { file ->
                    selectedHashes[file.hash] = _onOff.value.isChecked
                }.scan(emptyList<OmoideMemory>()) { acc, value -> acc + value }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        val selectedHashes = mutableStateMapOf<String, Boolean>()

        fun toggleSelection(hash: String) {
            selectedHashes[hash] = !(selectedHashes[hash] ?: false)
        }

        val _onOff: MutableStateFlow<OnOff> = MutableStateFlow(OnOff.On)
        val onOff: StateFlow<OnOff> = _onOff.asStateFlow()

        fun toggleAll(onOff: OnOff) {
            _onOff.value = onOff
            selectedHashes.forEach { (hash, _) ->
                selectedHashes[hash] = onOff.isChecked
            }
        }

        private val workManager = WorkManager.getInstance(application)

        val isDeleting: StateFlow<Boolean> =
            workManager.observeDeletingStateByManualTag(
                viewModelScope = viewModelScope,
            )
        val progress: StateFlow<Pair<Int, Int>?> =
            workManager.observeProgressByManualDelete(
                viewModelScope = viewModelScope,
            )

        fun enqueueDelete(hashes: Array<String>) {
            workManager.enqueueWManualDelete(
                hashes = hashes,
                totalCount = selectedHashes.count { it.value },
            )
        }
    }

@Composable
fun DriveFileDeleteRoute(
    viewModel: DriveFileDeleteViewModel = hiltViewModel(),
    toMainScreen: () -> Unit,
) {
    val pendingFiles by viewModel.pendingFiles.collectAsState()
    val onOff by viewModel.onOff.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val progress by viewModel.progress.collectAsState()
    var hasStartedDeleting by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(isDeleting) {
        if (!isDeleting && hasStartedDeleting) {
            toMainScreen()
        }
        if (isDeleting) {
            hasStartedDeleting = true
        }
    }

    DriveFileDeleteScreen(
        selectedHashes = viewModel.selectedHashes,
        pendingFiles = pendingFiles,
        onContentFixed = { hashes ->
            viewModel.enqueueDelete(hashes)
        },
        onToggle = { hash ->
            viewModel.toggleSelection(hash)
        },
        toMainScreen = toMainScreen,
        onOff = onOff,
        onSwitchChanged = { value ->
            viewModel.toggleAll(value)
        },
        isDeleting = isDeleting,
        progress = progress,
    )
}

@Composable
fun DriveFileDeleteScreen(
    selectedHashes: Map<String, Boolean>,
    pendingFiles: List<OmoideMemory>,
    onContentFixed: (hashes: Array<String>) -> Unit,
    onToggle: (hash: String) -> Unit,
    toMainScreen: () -> Unit,
    onOff: OnOff,
    onSwitchChanged: (OnOff) -> Unit,
    isDeleting: Boolean,
    progress: Pair<Int, Int>?,
) {
    Scaffold(
        topBar = { AppBarWithBackIcon(toMainScreen) },
        bottomBar = {
            Button(
                onClick = {
                    val hashes = selectedHashes.filter { it.value }.keys.toTypedArray()
                    onContentFixed(hashes)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                enabled = !isDeleting && selectedHashes.values.any { it },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("${selectedHashes.values.count { it }} 件を GDrive から削除")
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
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
                    key = { it.hash },
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
    if (isDeleting) {
        UploadIndicator(
            uploadProgress = progress,
        )
    }
}
