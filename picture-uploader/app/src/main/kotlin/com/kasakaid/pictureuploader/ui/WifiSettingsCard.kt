package com.kasakaid.pictureuploader.ui

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
import com.kasakaid.pictureuploader.data.OmoideUploadPrefsRepository
import com.kasakaid.pictureuploader.data.WifiRepository
import com.kasakaid.pictureuploader.data.WifiSetting
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


@HiltViewModel
class WifiSettingViewModel @Inject constructor(
    wifiRepository: WifiRepository,
    private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
) : ViewModel() {

    // 権限状態を管理する Flow
    private val _hasPermission = MutableStateFlow(false)

    // observeWifiSSID() を「実行」して、StateFlow に変換して保持する
    // これにより、リスナーの登録は「ViewModel の生存期間中、一回だけ」になる
    // callbackFlow は、誰かが collect（購読）を開始するたびに中身のブロックが最初から実行される**「Cold Flow」**です。
    // stateIn なし: 画面が再描画（Recomposition）されるたびに registerNetworkCallback と unregister が繰り返され、OS側が「短時間に何度も登録しすぎ」と判断して値を返さなくなったり、挙動が不安定になります。
    @OptIn(ExperimentalCoroutinesApi::class)
    val wifiState: StateFlow<WifiSetting> = _hasPermission.flatMapLatest { granted ->
        if (granted) {
            // 権限がある時だけ、OSの監視を開始する
            wifiRepository.observeWifiSSID()
        } else {
            flowOf(WifiSetting.Idle)
        }
    }.stateIn(
        scope = viewModelScope,
        // 誰も見なくなった後に、いつ監視（コネクション）を切るか
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WifiSetting.Idle,
    )

    // 権限が変わったことを外部から伝える
    fun updatePermissionStatus(isGranted: Boolean) {
        _hasPermission.value = isGranted
    }

    /**
     * 確定した Wifi の SSID を取得する
     */
    val _fixedSecureWifiSsid: MutableStateFlow<String?> = MutableStateFlow(
        omoideUploadPrefsRepository.getSecureWifiSsid()
    )
    val fixedSecureWifiSsid = _fixedSecureWifiSsid.asStateFlow()
    fun changeWifiState(ssid: String) {
        // 再開可能な関数はないです。値をセットすることでいきなりイベントが伝播しますよ
        _fixedSecureWifiSsid.value = ssid
        omoideUploadPrefsRepository.saveSecureWifiSsid(ssid)
    }
}

@Composable
fun WifiSettingsCardRoute(
    viewModel: WifiSettingViewModel = hiltViewModel(),
    isPermissionGranted: Boolean,
    // context, viewModel などを渡すとメモリリークが出るかもしれない。呼び出し先は関数などをもらうだけにする
    // context: Context,
) {
    // isPermissionGranted が変化するたびに ViewModel の中の Flow を叩き直す
    // LaunchedEffect に Unit を渡すと Composable が最初に実行されたときだけ動く、と言う挙動になる。
    // 一方で、引数を設定すると値が変わった時に動くことになる
    LaunchedEffect(isPermissionGranted) {
        viewModel.updatePermissionStatus(isPermissionGranted)
    }

    // by は Android の副作用を伝搬させるもの。値の監視は by を使うことによって行われる
    // 値がかわったことが監視されると copy を使って新規インスタンスが再度生成される
    // 値の変更が検知されたら。その Composable 関数だけが「新しい State インスタンス」を引数にして叩き直される。
    val wifiSetting by viewModel.wifiState.collectAsState()

    val fixedSecureWifiSsid by viewModel.fixedSecureWifiSsid.collectAsState()

    WifiSettingsCard(
        fixedSecureSsid = fixedSecureWifiSsid,
        wifiSetting = wifiSetting,
        onFixSecureSsid = {
            viewModel.changeWifiState(it.ssid)
        },
        isPermissionGranted = isPermissionGranted,
    )
}

@Composable
private fun WifiSettingsCard(
    fixedSecureSsid: String?,
    wifiSetting: WifiSetting,
    onFixSecureSsid: (WifiSetting.Found) -> Unit,
    isPermissionGranted: Boolean,
    // context, viewModel などを渡すとメモリリークが出るかもしれない。呼び出し先は関数などをもらうだけにする
//    context: Context,
//    viewModel: MainViewModel,
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
                        val currentState = if (wifiSetting.ssid == fixedSecureSsid) {
                            "✅ 設定済みの WiFi $fixedSecureSsid"
                        } else {
                            "設定されていない Wifi: ${wifiSetting.ssid}"
                        }
                        // --- 設定済みの場合 ---
                        Text(
                            "${currentState}に接続中です。",
                            color = Color(0xFF4CAF50)
                        )
                        if (wifiSetting.ssid == fixedSecureSsid) {
                            // 既存の設定を今回の Wifi に上書きする場合の選択肢
                            Button(onClick = { onFixSecureSsid(wifiSetting) }) { Text("現在の Wifi をセットする") }
                        }
                    } else {
                        Text(
                            text = AnnotatedString(text = "検出されたWi-Fi: ${wifiSetting.ssid}"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = { onFixSecureSsid(wifiSetting) },
                            modifier = Modifier.padding(top = 8.dp)
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
