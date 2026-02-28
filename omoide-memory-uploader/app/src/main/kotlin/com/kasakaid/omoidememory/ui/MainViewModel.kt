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
        private val _isAutoUploadEnabled =
            MutableStateFlow(omoideUploadPrefsRepository.isAutoUploadEnabled())
        val isAutoUploadEnabled: StateFlow<Boolean> = _isAutoUploadEnabled.asStateFlow()

        // 権限状態を管理する Flow
        private val hasPermission = MutableStateFlow(false)

        // Google サインイン状態
        private val isGoogleSignIn = MutableStateFlow(false)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val wifiState: StateFlow<WifiSetting> =
            hasPermission
                .flatMapLatest { granted ->
                    if (granted) {
                        // 権限がある時だけ、OSの監視を開始する
                        wifiRepository.observeWifiSSID()
                    } else {
                        flowOf(WifiSetting.Idle)
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
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
         * 【Eagerly の必要性】
         * 写真選択などの別画面からホームに戻った際、Wi-Fi 取得の「待ち時間」が発生して一瞬「未接続」
         * に見えるのを防ぐためです。Started.Eagerly を使うことで、ViewModel が生存している間は
         * バックグラウンドでも監視を継続し、画面復帰時に最新の状態を瞬時に提供します。
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
                started = SharingStarted.Eagerly,
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
                started = SharingStarted.Eagerly,
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
            omoideUploadPrefsRepository.setAutoUploadEnabled(enabled)
            _isAutoUploadEnabled.value = enabled

            if (enabled) {
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
