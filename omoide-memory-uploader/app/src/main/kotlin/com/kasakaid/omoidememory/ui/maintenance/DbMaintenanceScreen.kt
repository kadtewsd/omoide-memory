package com.kasakaid.omoidememory.ui.maintenance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kasakaid.omoidememory.ui.AppBarWithBackIcon

// TODO: PR-3 で実装
@Composable
fun DbMaintenanceScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            AppBarWithBackIcon(
                title = "DB メンテナンス",
                onFinished = onBack,
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
