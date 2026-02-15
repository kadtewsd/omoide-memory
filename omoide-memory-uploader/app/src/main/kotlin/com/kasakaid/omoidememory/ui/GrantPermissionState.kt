package com.kasakaid.omoidememory.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.kasakaid.omoidememory.ui.GrantPermissionState.Companion.checkInitialPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * モバイルアプリの権限移譲状況
 */

sealed interface GrantPermissionState {
    val message: String
    val resultType: ResultType

    companion object {

        /**
         * 現状の Permission を確認する
         */
        fun checkInitialPermission(
            context: Context,
            checkTargetPermissions: Array<String>,
        ): GrantPermissionState {
            // 全ての権限が「GRANTED」か確認する
            return checkTargetPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }.let {
                if (it) {
                    Granted
                } else {
                    NotGranted
                }
            }
        }
    }

    object NotGranted : GrantPermissionState {
        override val message = "コンテンツをアップロードするために携帯に触れる権限が必要"
        override val resultType: ResultType = ResultType.NotStill
    }


    object Granted : GrantPermissionState {
        override val message = "権限が許可されました"
        override val resultType: ResultType = ResultType.Success
    }

    object Failure : GrantPermissionState {
        override val message = "権限が許可されませんでした"
        override val resultType: ResultType = ResultType.Failure
    }
}


@HiltViewModel
class GrantPermissionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    // 1. この ViewModel が「責任を持つ」権限リストを定義
    private val requiredPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) +
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

    private val _grantPermissionState = MutableStateFlow(
        checkInitialPermission(
            context = context,
            checkTargetPermissions = requiredPermissions,
        )
    )
    val grantPermissionState: StateFlow<GrantPermissionState> = _grantPermissionState.asStateFlow()

    fun changeGrantPermissionState(state: GrantPermissionState): GrantPermissionState {
        _grantPermissionState.value = state
        return state
    }

    // ViewModel内に追加
    fun refreshPermissionStatus() {
        _grantPermissionState.value = checkInitialPermission(
            context = context,
            checkTargetPermissions = requiredPermissions,
        )
    }
}


@Composable
fun GrantPermissionRoute(
    viewModel: GrantPermissionViewModel = hiltViewModel(),
// ★ 状態が変わったことを親に通知するコールバック。権限状態を親に伝搬する State Hoisting
    onPermissionChanged: (GrantPermissionState) -> Unit = {}
) {

    // rememberLauncherForActivityResult が発生した後のコールバックとして値変わるよ、ということを示している
    val permissionState by viewModel.grantPermissionState.collectAsState()

    // 初回描画時のみ API 実行するためのおまじない。LaunchedEffect で初回だけ実行してくれる
    LaunchedEffect(permissionState) {
        viewModel.refreshPermissionStatus()
    }
    // Permissions Section
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        viewModel.changeGrantPermissionState(
            if (allGranted) {
                GrantPermissionState.Granted
            } else {
                GrantPermissionState.Failure
            }
        ).also {
            onPermissionChanged(it)
        }
    }
    GrantPermissionStateCard(
        permissionLauncher = permissionLauncher,
        permissionState = permissionState,
        )
}

@Composable
fun GrantPermissionStateCard(
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    permissionState: GrantPermissionState,
) {
    // 画面全てを占有するので Modifier.fillMaxSize() は使わない!
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "1. 権限の設定とセットアップ",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION, // For Wi-Fi SSID
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                )
            }) {
                Text("権限を与えよう！")
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            permissionState.message, color = colorOf(permissionState.resultType)
        )
    }
}
