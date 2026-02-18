package com.kasakaid.omoidememory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun UploadIndicator(
    uploadProgress: Pair<Int, Int>?,
) {

    // 背景を少し白くして、クリックを無効化する
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.7f)).pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 🚀 進捗データがあるかどうかで表示を切り替える
            if (uploadProgress != null && uploadProgress.second > 0) {
                val (current, total) = uploadProgress
                // total が 0 の時は 0f を、それ以外は進捗を計算
                val progressValue = if (total > 0) current.toFloat() / total.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progressValue.coerceIn(0f, 1f) },
                    modifier = Modifier.width(200.dp)
                )
                Text("$current / $total アップロード中...")
            } else {
                // まだハッシュ計算中や起動待ちの時はグルグル
                CircularProgressIndicator()
                Text("準備中...")
            }
        }
    }
}