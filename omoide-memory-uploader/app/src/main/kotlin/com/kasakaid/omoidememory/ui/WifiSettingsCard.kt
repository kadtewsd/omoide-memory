package com.kasakaid.omoidememory.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.data.WifiRepository
import com.kasakaid.omoidememory.data.WifiSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Composable
fun WifiSettingsCard(
    fixedSecureSsid: String?,
    wifiSetting: WifiSetting,
    onFixSecureSsid: (String) -> Unit,
    isPermissionGranted: Boolean,
) {
    if (!isPermissionGranted) {
        Text("接続許可がないため Wifi を取得できないです。")
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("3. Wi-Fi 設定", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("安全な Wifi の SSID: ${fixedSecureSsid ?: "未設定"}")

            // --- 未設定の場合 ---
            Text("アップロードを許可するWi-Fiがまだ設定されていません。", color = Color.Gray)
            Text(wifiSetting.message, color = Color.Gray)
            when (wifiSetting) {
                is WifiSetting.Idle -> {
                    Text("接続中のWi-Fiを検出")
                }

                is WifiSetting.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                is WifiSetting.Found -> {
                    // 今接続中のWi-Fiがある場合
                    if (fixedSecureSsid != null) {
                        val currentState =
                            if (wifiSetting.ssid == fixedSecureSsid) {
                                "✅ 設定済みの WiFi $fixedSecureSsid"
                            } else {
                                "設定されていない Wifi: ${wifiSetting.ssid}"
                            }
                        // --- 設定済みの場合 ---
                        Text(
                            "${currentState}に接続中です。",
                            color = Color(0xFF4CAF50),
                        )
                        if (wifiSetting.ssid == fixedSecureSsid) {
                            // 既存の設定を今回の Wifi に上書きする場合の選択肢
                            Button(onClick = { onFixSecureSsid(wifiSetting.ssid) }) { Text("現在の Wifi をセットする") }
                        }
                    } else {
                        Text(
                            text = AnnotatedString(text = "検出されたWi-Fi: ${wifiSetting.ssid}"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = { onFixSecureSsid(wifiSetting.ssid) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            // ここに入るたびに、onFixSecureSsid をするとなにかをするたびに無駄に動く。ボタンを押した時だけにする
                            Text("${wifiSetting.ssid} を安全なWi-Fiに設定する")
                        }
                    }
                }

                // Wifi の状態を取得できなかったようです
                // Wi-Fiが取れていない場合
                is WifiSetting.NotConnected, WifiSetting.NotFound -> {
                    Text(text = wifiSetting.message, color = MaterialTheme.colorScheme.error)
                    Text("現在の Wifi の SSID: 取得できず。WifiRepository の状態が変わったら再施行されます")
                }
            }
        }
    }
}
