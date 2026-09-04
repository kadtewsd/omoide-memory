package com.kasakaid.omoidememory.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kasakaid.omoidememory.ui.theme.OmoideMemoryTheme
import com.kasakaid.omoidememory.worker.WorkerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * 通知タップ等で外部から指定された初期遷移先（[InitialRoute]）を保持する StateFlow。
     * AppRouter に渡され、画面遷移が完了した時点で onRouteConsumed コールバックにより [InitialRoute.MAIN] にリセットされる。
     */
    private val targetRoute = MutableStateFlow<InitialRoute>(InitialRoute.MAIN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            OmoideMemoryTheme {
                val route by targetRoute.collectAsState()
                AppRouter(
                    initialRoute = route,
                    onRouteConsumed = { targetRoute.value = InitialRoute.MAIN },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * 送信されてきた Intent を解析し、通知タップ等による画面遷移指定（EXTRA_ROUTE）が含まれていれば
     * 対応する [InitialRoute] を解決して [targetRoute] にセットします。
     */
    private fun handleIntent(intent: Intent?) {
        when {
            intent?.hasExtra(WorkerHelper.EXTRA_ROUTE) == true -> {
                val routeName = intent.getStringExtra(WorkerHelper.EXTRA_ROUTE)
                targetRoute.value = InitialRoute.fromRoute(routeName)
            }
        }
    }
}
