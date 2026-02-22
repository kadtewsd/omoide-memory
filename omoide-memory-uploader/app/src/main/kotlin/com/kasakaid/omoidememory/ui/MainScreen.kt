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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    onNavigateToDriveDelete: () -> Unit,
) {
    val isAutoUploadEnabled by viewModel.isAutoUploadEnabled.collectAsState()
    // 親で「現在、権限があるか」という事実を覚えておく
    // ViewModel で持つべき場合:
    // 「権限の有無によって、DBの値を書き換える」「権限の状態をログとしてサーバーに送る」など、UIの外側でもその情報が必要な場合。
    // remember でいい場合:
    // 「権限が取れたら下のボタンを活性化する」といった、その画面内での表示の切り替えにしか使わない場合。
    val context = LocalContext.current
    // 軽く remember で画面描画時の時だけ覚えておく。回転させると忘れるがまぁよいでしょう。
    val wifiPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    var isGranted by remember {
        mutableStateOf(
            isWifiPermissionGranted(
                GrantPermissionState.checkInitialPermission(
                    context = context,
                    checkTargetPermissions = wifiPermissions,
                ),
            ),
        )
    }

    var isGoogleSignIn by remember {
        mutableStateOf(
            GoogleSignInState.checkGoogleSignInStatus(context) is GoogleSignInState.Synced,
        )
    }

    val scrollState = rememberScrollState() //

    val isUploading = viewModel.isUploading.collectAsState().value
    val progress = viewModel.progress.collectAsState().value

    /**
     * 一括アップロードされたか？
     */
    var hasStartedUploading by remember {
        mutableStateOf(false)
    }

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
            isGranted =
                isWifiPermissionGranted(
                    GrantPermissionState.checkInitialPermission(
                        context = context,
                        checkTargetPermissions = wifiPermissions,
                    ),
                )
        })
        // Google のサインインの状態
        GoogleAuthStateRoute(onSignInSuccess = {
            isGoogleSignIn = it
        })
        // Wi-Fi Configuration Section
        WifiSettingsCardRoute(
            // permissionState が Granted に変わったかどうかを中で監視させる
            isPermissionGranted = isGranted,
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
            canUpload = isGranted && isGoogleSignIn,
            onNavigateToContentSelection = onNavigateToSelection,
        )

        // GDrive のファイルを削除するボタンを追加
        Button(
            onClick = onNavigateToDriveDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("GDrive 上のファイルを削除")
        }
    }
    // 🚀 アップロード中のみ表示されるロック層
    if (isUploading) {
        UploadIndicator(
            uploadProgress = progress,
        )
    }
}
