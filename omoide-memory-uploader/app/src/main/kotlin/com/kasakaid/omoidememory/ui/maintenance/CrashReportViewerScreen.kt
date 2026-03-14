package com.kasakaid.omoidememory.ui.maintenance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kasakaid.omoidememory.ui.AppBarWithBackIcon

@Composable
fun CrashReportViewerScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            AppBarWithBackIcon(
                title = "スタックトレース確認",
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
            Text("スタックトレース一覧画面 (Stub)")
        }
    }
}
