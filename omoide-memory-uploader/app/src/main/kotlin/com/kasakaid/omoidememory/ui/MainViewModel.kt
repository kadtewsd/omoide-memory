package com.kasakaid.omoidememory.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.data.WifiRepository
import com.kasakaid.omoidememory.data.WifiSetting
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeProgressByManual
import com.kasakaid.omoidememory.extension.WorkManagerExtension.observeUploadingStateByManualTag
import com.kasakaid.omoidememory.worker.AutoGDriveUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Wi-Fi 関連の状態を統合したデータクラス。
 * [setting] 現在の接続状態
 * [fixedSsid] ユーザーが設定した「安全な Wi-Fi」の SSID
 * [isValid] アップロード条件（SSID が一致しているか）を満たしているか
 */
data class WifiStatus(
    val setting: WifiSetting = WifiSetting.Idle,
    val fixedSsid: String? = null,
    val isValid: Boolean = false,
)

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        application: Application,
        private val wifiRepository: WifiRepository,
        private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
    ) : ViewModel() {
        // 一旦、自動アップロード機能は利用しないため false 固定とする。
        // 将来的な参照のためにコードは残しておくが、初期値は常に false。
        private val _isAutoUploadEnabled = MutableStateFlow(false)
        val isAutoUploadEnabled: StateFlow<Boolean> = _isAutoUploadEnabled.asStateFlow()

        // 権限状態を管理する Flow
        private val hasPermission = MutableStateFlow(false)

        // Google サインイン状態
        private val isGoogleSignIn = MutableStateFlow(false)

        // Wi-Fi 状況を強制的に再取得するためのトリガー
        private val refreshTrigger = MutableStateFlow(0)

        /**
         * 外部（UI）から Wi-Fi 状況の更新を明示的に要求します。
         * 画面が foreground に復帰した際などに呼び出すことを想定しています。
         */
        fun refreshWifiStatus() {
            refreshTrigger.value++
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val wifiState: StateFlow<WifiSetting> =
            combine(
                hasPermission,
                refreshTrigger,
            ) { granted, trigger -> granted to trigger }
                // 🚀 Point: 権限状態やリフレッシュ要求が「実際に変化した時」だけ後続へ流す。
                // これがないと、同じ値が流れてきた際にも flatMapLatest が走り、OS へのコールバック登録が重複・頻発して
                // TooManyRequestsException を引き起こす原因になります。
                .distinctUntilChanged()
                .flatMapLatest { (granted, _) ->
                    if (granted) {
                        // 🚀 WifiRepository.observeWifiSSID() は OS (ConnectivityManager) からの
                        // リアルタイムな通知を callbackFlow で検知して emit してくれます。
                        // そのため、ViewModel 側でループを回して自発的に再取得する必要はありません。
                        wifiRepository.observeWifiSSID()
                    } else {
                        flowOf(WifiSetting.Idle)
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = WifiSetting.Idle,
                )

        /**
         * Wi-Fi 関連の状態を統合した Flow。
         *
         * 【統合の理由】
         * OS からの物理的な接続状態 (wifiState)、ユーザーが設定した信頼できる SSID (fixedSsid)、
         * そしてその両者を組み合わせた「アップロード許可判定」 (isValid) は密接に関連しています。
         * これらを一つのデータ構造に集約することで、UI 側で複数の Flow を管理する複雑さを排除し、
         * 単一のソース・オブ・トゥルース (SSOT) として整合性を担保します。
         *
         * 【リアクティビティの保証】
         * 1. 突如 SIM に切り替わったり、別の Wi-Fi に接続された場合、OS のコールバック (connectivityManager)
         *    を起点として `wifiState` が更新されます。これにより `combine` が即座に走り、`isValid` も
         *    同期して更新されるため、UI のボタンは即座に無効化 / 有効化されます。
         *
         * 【WhileSubscribed(5000) の採用】
         * かつては画面復帰時の「待ち時間」を防ぐために Eagerly を使用していましたが、
         * 現在は 5 秒おきの自動更新と OS コールバックの監視を組み合わせているため、
         * 画面が表示されていない間（購読者がいない間）はリソースを節約する方が適切です。
         * WhileSubscribed(5000) を使うことで、画面回転などの一時的な購読解除ではフローを停止せず、
         * 別画面への遷移時などは 5 秒後に監視を停止して電池消費を抑えます。
         * 画面復帰時には即座に購読が再開されるため、最新の状態が瞬時に提供されます。
         */
        val wifiStatus: StateFlow<WifiStatus> =
            combine(
                wifiState,
                omoideUploadPrefsRepository.getSecureWifiSsidFlow(),
            ) { state, fixedSsid ->
                WifiStatus(
                    setting = state,
                    fixedSsid = fixedSsid,
                    isValid = fixedSsid != null && state is WifiSetting.Found && state.ssid == fixedSsid,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue =
                    WifiStatus(
                        setting = WifiSetting.Idle,
                        fixedSsid = omoideUploadPrefsRepository.getSecureWifiSsid(),
                        isValid = false,
                    ),
            )

        fun updatePermissionStatus(isGranted: Boolean) {
            hasPermission.value = isGranted
        }

        fun updateGoogleSignInStatus(isSynced: Boolean) {
            isGoogleSignIn.value = isSynced
        }

        /**
         * アップロードに必要なすべての条件を統合した Flow。
         * UI はこれひとつを監視するだけで、ボタンの有効化やエラーメッセージの表示を
         * リアクティブに行うことができます。
         */
        val uploadCondition: StateFlow<UploadRequiredCondition> =
            combine(
                hasPermission,
                isGoogleSignIn,
                wifiStatus,
            ) { permission, signIn, wifi ->
                UploadRequiredCondition(
                    isPermissionGranted = permission,
                    isGoogleSignIn = signIn,
                    isWifiValid = wifi.isValid,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue =
                    UploadRequiredCondition(
                        isPermissionGranted = hasPermission.value,
                        isGoogleSignIn = isGoogleSignIn.value,
                        isWifiValid = wifiStatus.value.isValid,
                    ),
            )

        fun changeWifiSsid(ssid: String) {
            omoideUploadPrefsRepository.saveSecureWifiSsid(ssid)
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

        fun toggleAutoUpload(enabled: Boolean) {
            // 自動アップロードは現在利用しないため、常に無効化する。
            // 参照実装としてロジックは残すが、外部からの変更は受け付けない。
            val fixedEnabled = false
            omoideUploadPrefsRepository.setAutoUploadEnabled(fixedEnabled)
            _isAutoUploadEnabled.value = fixedEnabled

            if (fixedEnabled) {
                enqueuePeriodicWork()
            } else {
                workManager.cancelUniqueWork("AutoUploadWork")
            }
        }

        /**
         * 自動アップロードをキューイングするメソッド
         */
        private fun enqueuePeriodicWork() {
            // wifi の時だけワーカーを実行してね、という印
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val uploadWorkRequest =
                PeriodicWorkRequestBuilder<AutoGDriveUploadWorker>(1, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

            workManager.enqueueUniquePeriodicWork(
                "AutoUploadWork",
                ExistingPeriodicWorkPolicy.KEEP,
                uploadWorkRequest,
            )
        }
    }
