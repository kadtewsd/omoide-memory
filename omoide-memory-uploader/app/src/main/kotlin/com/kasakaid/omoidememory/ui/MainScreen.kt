package com.kasakaid.omoidememory.ui

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// 1. 判定用の小さな関数を定義（MainScreen 内、または companion 内）
fun isWifiPermissionGranted(state: GrantPermissionState): Boolean = state is GrantPermissionState.Granted

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToSelection: () -> Unit,
) {
    val wifiStatus by viewModel.wifiStatus.collectAsState()
    val uploadCondition by viewModel.uploadCondition.collectAsState()

    // 🚀 初回起動時のみ現在の状態を確認して ViewModel に教える
    LaunchedEffect(Unit) {
        val initialPermission =
            isWifiPermissionGranted(
                GrantPermissionState.checkInitialPermission(
                    context = context,
                    checkTargetPermissions = wifiPermissions,
                ),
            )
        viewModel.updatePermissionStatus(initialPermission)

        val initialSignIn =
            GoogleSignInState.checkGoogleSignInStatus(context) is GoogleSignInState.Synced
        viewModel.updateGoogleSignInStatus(initialSignIn)
    }

    val scrollState = rememberScrollState()

    val isUploading = viewModel.isUploading.collectAsState().value
    val progress = viewModel.progress.collectAsState().value

    /**
     * 一括アップロードされたか？
     */
    var hasStartedUploading by remember {
        mutableStateOf(false)
    }

    val isAutoUploadEnabled by viewModel.isAutoUploadEnabled.collectAsState()

    LaunchedEffect(isUploading) {
        /**
         * 手動でアップロードが完了していたら再度候補を取得するため
         * 一括アップロードが完了したら画面を再描画して現状のファイルのアップロード状況を表示する
         */
        if (!isUploading && hasStartedUploading) {
            // 一括アップロードが完了したとみなす。そのため、フラグを落として、アップロードが始まってない状態にする
            hasStartedUploading = false
        }
        if (isUploading) {
            // アップロードが開始したら開始状態にする
            hasStartedUploading = true
        }
    }

    val wifiStatus by viewModel.wifiStatus.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize() // 画面全体を占有
                .padding(16.dp) // 全体に余白
                .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp), // 各要素の間に隙間を作る
    ) {
        // Android の権限コンポーネント
        GrantPermissionRoute(onPermissionChanged = {
            // 最低限 Wifi が入っているかのチェックを、State Hoisting でチェック!
            val current =
                isWifiPermissionGranted(
                    GrantPermissionState.checkInitialPermission(
                        context = context,
                        checkTargetPermissions = wifiPermissions,
                    ),
                )
            viewModel.updatePermissionStatus(current)
        })
        // Google のサインインの状態
        GoogleAuthStateRoute(onSignInSuccess = {
            viewModel.updateGoogleSignInStatus(it)
        })
        // Wi-Fi Configuration Section
        WifiSettingsCard(
            wifiSetting = wifiStatus.setting,
            fixedSecureSsid = wifiStatus.fixedSsid,
            onFixSecureSsid = { viewModel.changeWifiSsid(it) },
            isPermissionGranted = uploadCondition.isPermissionGranted,
        )

        // Auto Upload Toggle
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "コンテンツの自動アップロードは?",
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = isAutoUploadEnabled,
                    onCheckedChange = { viewModel.toggleAutoUpload(it) },
                )
            }
        }

        // 基準日設定
        UploadedBaseLineRoute()

        // Status & Trigger
        UploadStatusRoute(
            condition = uploadCondition,
            onNavigateToContentSelection = onNavigateToSelection,
        )
    }
    // 🚀 アップロード中のみ表示されるロック層
    if (isUploading) {
        UploadIndicator(
            uploadProgress = progress,
        )
    }
}
