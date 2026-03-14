package com.kasakaid.omoidememory.maintenance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kasakaid.omoidememory.ui.AppBarWithBackIcon
import com.kasakaid.omoidememory.ui.theme.OmoideMemoryTheme

// TODO: PR-3 で実装
class DbMaintenanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmoideMemoryTheme {
                Scaffold(
                    topBar = {
                        AppBarWithBackIcon(
                            title = "DB メンテナンス",
                            onFinished = { finish() },
                        )
                    },
                ) { padding ->
                    Box(
                        modifier =
                            Modifier
                                .padding(padding)
                                .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("DB メンテナンス画面 (Stub)")
                    }
                }
            }
        }
    }
}
