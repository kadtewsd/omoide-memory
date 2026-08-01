package com.kasakaid.omoidememory.ui.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kasakaid.omoidememory.ui.AppBarWithBackIcon

@Composable
fun MaintenanceScreen(
    onBack: () -> Unit,
    onNavigateToCrashReport: () -> Unit,
    onNavigateToDbMaintenance: () -> Unit,
    appVersion: String = "1.7.3",
) {
    Scaffold(
        topBar = {
            AppBarWithBackIcon(
                title = "メンテナンス",
                onFinished = onBack,
                actions = {
                    Text(
                        text = "v$appVersion",
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = onNavigateToCrashReport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("スタックトレース確認")
            }
            Button(
                onClick = onNavigateToDbMaintenance,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("DB メンテナンス")
            }
        }
    }
}
