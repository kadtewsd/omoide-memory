package com.kasakaid.omoidememory.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class UploadSettingViewModel @Inject constructor(
    private val prefsRepository: OmoideUploadPrefsRepository
) : ViewModel() {

    // 初期値を取得（nullならエポック0など適宜調整）
    private val _baselineInstant = MutableStateFlow(prefsRepository.getUploadBaseLineInstant())
    val baselineInstant: StateFlow<Instant?> = _baselineInstant.asStateFlow()

    // UI表示用に整形した文字列（YYYY/MM/DD）
    val displayDate: StateFlow<String> = _baselineInstant.map { instant ->
        instant?.let {
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
                .withZone(ZoneId.systemDefault())
                .format(it)
        } ?: "未設定"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "未設定")

    /**
     * 基準日が決まったタイミングのメソッド
     */
    fun onDateSelected(utcMillis: Long?) {
        utcMillis ?: return

        // 1. 返ってきた UTCミリ秒を LocalDate (日付のみ) に変換
        // これで「ユーザーが選んだカレンダー上の日付」が確定する
        val localDate = Instant.ofEpochMilli(utcMillis)
            .atZone(ZoneId.of("UTC")) // DatePicker は UTC で日付を出すのでここまでは UTC
            .toLocalDate()

        // 2. その日付の「システムデフォルト時間での開始時間 (00:00)」を Instant にする
        // これで JST 00:00:00 の Instant が手に入る
        val startOfDayInstant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()

        viewModelScope.launch {
            prefsRepository.updateUploadBaseLineInstant(startOfDayInstant)
            _baselineInstant.value = startOfDayInstant
        }
    }
}

/**
 * アップロードの基準となるコンテンツ作成日を決めます。
 * この日付より前に作成されたコンテンツはアップロード対象になりません。
 */
@Composable
fun UploadedBaseLineRoute(
    viewModel: UploadSettingViewModel = hiltViewModel()
) {
    val displayDate by viewModel.displayDate.collectAsState()

    UploadDateCard(
        displayDate = displayDate,
        onDateSelected = { millis ->
            viewModel.onDateSelected(millis)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadDateCard(
    displayDate: String,
    onDateSelected: (Long?) -> Unit
) {
    var showModal by remember { mutableStateOf(false) }

    // DatePickerの状態管理。初期値に現在地を入れるなどの設定が可能
    val datePickerState = rememberDatePickerState()

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "アップロード対象の基準日",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "この日付以降に撮影されたファイルが対象になります",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showModal = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "基準日: $displayDate")
            }
        }
    }

    if (showModal) {
        DatePickerDialog(
            onDismissRequest = { showModal = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateSelected(datePickerState.selectedDateMillis)
                    showModal = false
                }) { Text("決定") }
            },
            dismissButton = {
                TextButton(onClick = { showModal = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}