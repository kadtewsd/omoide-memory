package com.kasakaid.pictureuploader.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.kasakaid.pictureuploader.data.OmoideUploadPrefsRepository
import com.kasakaid.pictureuploader.worker.GdriveUploadWorker.Companion.TAG
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject


/**
 * Google アカウントのサインインの状態
 */
sealed interface GoogleSignInState {
    val message: String
    val resultType: ResultType

    object NotSynced : GoogleSignInState {
        override val message = "Google アカウントが同期されていません"
        override val resultType: ResultType = ResultType.NotStill
    }

    class Synced(email: String?) : GoogleSignInState {
        override val message = "Google アカウントが同期されました $email"
        override val resultType: ResultType = ResultType.Success
    }

    class Failure(expression: Exception) : GoogleSignInState {
        override val message = "Google アカウントが同期が失敗しました ${expression.message}"
        override val resultType: ResultType = ResultType.Failure
    }

    companion object {
        /**
         * 現在の Google のサインインの状態を調べます
         */
        fun checkGoogleSignInStatus(context: Context): GoogleSignInState {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            return if (account != null && account.email != null) {
                // すでにログイン済みの情報が見つかった
                Synced(account.email)
            } else {
                // 未サインイン
                NotSynced
            }
        }
    }
}

@HiltViewModel
class AuthStateViewModel @Inject constructor(
    private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
    @param: ApplicationContext private val context: Context,
) : ViewModel() {

    private val _accountName = MutableStateFlow(omoideUploadPrefsRepository.getAccountName())
    val accountName: StateFlow<String?> = _accountName.asStateFlow()

    fun updateAccountName(name: String?, onSignInSuccess: (Boolean) -> Unit) {
        omoideUploadPrefsRepository.setAccountName(name)
        _accountName.value = name
        _googleSignInState.value = if (name == null) GoogleSignInState.NotSynced else GoogleSignInState.Synced(name)
        onSignInSuccess(name != null)
    }

    /**
     * 画面初期描画時のメソッド
     */
    fun refreshAuthState() {
        _accountName.value = omoideUploadPrefsRepository.getAccountName()
        _googleSignInState.value = GoogleSignInState.checkGoogleSignInStatus(context)
    }

    private val _googleSignInState = MutableStateFlow(GoogleSignInState.checkGoogleSignInStatus(context))
    val googleSignInState: StateFlow<GoogleSignInState> = _googleSignInState.asStateFlow()


    /**
     * Google のサインインランチャーが完了した後の副作用。
     * remember で覚えているとその画面が出ている時だけだが、state で管理するとずっと状態が管理される
     */
    fun handleSignInResult(task: Task<GoogleSignInAccount>, onSignInSuccess: (Boolean) -> Unit) {
         try {
            val account = task.getResult(Exception::class.java)
            updateAccountName(account?.email, onSignInSuccess)
        } catch (e: Exception) {
            Log.i(TAG, "${e.message} ${e.stackTrace.toString()}")
            _googleSignInState.value = GoogleSignInState.Failure(e)
        }
    }

}


@Composable
fun GoogleAuthStateRoute(
    viewModel: AuthStateViewModel = hiltViewModel(),
    onSignInSuccess: (Boolean) -> Unit,
) {
    val accountName by viewModel.accountName.collectAsState()
    val googleSignInState by viewModel.googleSignInState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshAuthState()
    }
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            viewModel.handleSignInResult(task, onSignInSuccess)
        }
    }

    // LocalContext.current は composable のスコープでしかアクセスできないよ！
    val context = LocalContext.current
    GoogleAuthState(
        accountName = accountName,
        googleSignInState = googleSignInState,
        signInForGoogle = {
            val gso =
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                    .build()
            val client = GoogleSignIn.getClient(context, gso)
            signInLauncher.launch(client.signInIntent)
        },
        signOutFromGoogle = {
            viewModel.updateAccountName(null)
        }
    )
}


@Composable
private fun GoogleAuthState(
    accountName: String?,
    googleSignInState: GoogleSignInState,
    signInForGoogle: () -> Unit,
    signOutFromGoogle: () -> Unit
) {

    // Google Auth Section
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("2. Googleのアカウント", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (accountName == null) {
                Button(onClick = signInForGoogle) {
                    Text("GDrive のアカウントへサインインしよう！")
                }
            } else {
                Text("接続完了: $accountName")
                Button(onClick = signOutFromGoogle) {
                    Text("サインアウト")
                }
            }
        }
    }
    Text(
        googleSignInState.message, color = colorOf(googleSignInState.resultType)
    )
}