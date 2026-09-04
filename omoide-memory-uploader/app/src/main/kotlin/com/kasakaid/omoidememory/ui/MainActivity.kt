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
     * 通知タップ等で外部から指定された遷移先ルート（例: "pending"）を保持する StateFlow。
     * AppRouter に渡され、画面遷移が完了した時点で onRouteConsumed コールバックにより null にクリアされる。
     */
    private val targetRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            OmoideMemoryTheme {
                val route by targetRoute.collectAsState()
                AppRouter(
                    initialRoute = route,
                    onRouteConsumed = { targetRoute.value = null },
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
     * [targetRoute] にセットして AppRouter へディスパッチします。
     */
    private fun handleIntent(intent: Intent?) {
        when {
            intent?.hasExtra(WorkerHelper.EXTRA_ROUTE) == true -> {
                targetRoute.value = intent.getStringExtra(WorkerHelper.EXTRA_ROUTE)
            }
        }
    }
}
