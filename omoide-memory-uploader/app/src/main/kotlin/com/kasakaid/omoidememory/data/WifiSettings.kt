package com.kasakaid.omoidememory.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Wifi 探索状態を表す ADT (Sealed Interface)
sealed interface WifiSetting {
    val message: String

    // 初期状態
    object Idle : WifiSetting {
        override val message: String = "初期状態です"
    }

    // 取得中
    object Loading : WifiSetting {
        override val message: String = "取得中です"
    }

    class Found(
        val ssid: String,
    ) : WifiSetting {
        override val message: String = "見つかりました！"
    }

    object NotFound : WifiSetting {
        override val message = "Wi-Fi名は取得できませんでした。GPSをONにしてください。"
    }

    object NotConnected : WifiSetting {
        override val message = "Wi-Fiに接続されていません。"
    }
}

@Singleton
class WifiRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        // Flow で返す意味
        // callbackFlow 関数は「Flow を作成して返す関数」として定義されている。
        // 川を流しっぱなしにするような形にしてして提供して呼び出し元に利用させる。
        // 呼び出し元は、この川が一回だけできたら何度も作らせてはいけないので、stateIn でリアクティブに ViewModel 側でコールしつつも、
        // 最終的な利用者は、collectAsState にて viewModel 経由で使用すること。これによりリアクティブに値が変わったら再度画面の描画が発生できる

        // connectivityManager のコールバック
        // connectivityManager に callBack を登録して、応答が来たら、 onCapabilitiesChanged に入ってきて、trySend で結果が格納。
        // 呼び出し元は、Flow から ssid を取り出す。
        fun observeWifiSSID(): Flow<WifiSetting> =
            callbackFlow {
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                val callback =
                    @RequiresApi(Build.VERSION_CODES.S)
                    object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                        override fun onAvailable(network: Network) {
                            super.onAvailable(network)
                            trySend(WifiSetting.Loading)
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            caps: NetworkCapabilities,
                        ) {
                            val wifi = (caps.transportInfo as? WifiInfo) ?: return
                            val ssid = wifi.ssid.replace("\"", "")

                            val result =
                                if (ssid != "<unknown ssid>") {
                                    WifiSetting.Found(ssid)
                                } else {
                                    WifiSetting.NotFound
                                }
                            trySend(result)
                            // 【修正】これ以上データは送らない... という考えを捨てて、監視を続けるようにする。
                            // channel.close() を呼ぶと Flow が終了してしまうため、削除。
                        }

                        override fun onUnavailable() {
                            trySend(WifiSetting.NotConnected)
                            // channel.close() を削除
                        }

                        /**
                         * Wifi に接続していたが、外に出るなどして切れてしまった場合。この場合は Lost にする
                         */
                        override fun onLost(network: Network) {
                            // 【ここが重要！】Wi-Fiが切れた（SIMに切り替わった等）
                            // 明示的に「未接続」を流す
                            trySend(WifiSetting.Idle)
                        }
                    }
                // 監視だけ開始
                // awaitClose は このストリームを閉じずに、ここで待機せよ」という命令
                // awaitClose がない場合
                // 即時終了: 関数が最後まで走りきってしまい、callbackFlow が即座に閉じてしまいます。
                // イベントが届かない: 後から Wi-Fi が見つかって onCapabilitiesChanged が呼ばれても、流す先の「川（Flow）」がすでに干上がっている状態になります。
                // メモリリーク: unregisterNetworkCallback を呼ぶタイミングを失い、リスナーが OS 内に残ったままになります。
                // Android 12+ の非同期取得
                // registerDefaultNetworkCallback は WorkManager などの他ライブラリも多用するため、
                // アプリ側では Wi-Fi に特化した NetworkRequest を使うことで Callback 数上限 (TooManyRequestsException)
                // の競合を回避します。
                val request =
                    android.net.NetworkRequest
                        .Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build()
                connectivityManager.registerNetworkCallback(request, callback)
                // ViewModel の scope が終わるまで監視を続ける
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }

        fun isConnectedToWifi(): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }

        /**
         * 現在の Wi-Fi 接続状況を一回だけ取得します。
         * [observeWifiSSID] を再利用することで、API 31+ で必要な FLAG_INCLUDE_LOCATION_INFO が正しく反映された
         * 結果を取得できることを保証します。
         */
        suspend fun snapshotSsid(): WifiSetting =
            observeWifiSSID()
                .filter { it !is WifiSetting.Loading && it !is WifiSetting.Idle }
                .first()
    }
