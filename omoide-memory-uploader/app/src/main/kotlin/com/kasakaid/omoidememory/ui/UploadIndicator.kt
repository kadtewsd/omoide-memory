package com.kasakaid.omoidememory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

const val CONTENTS_UPLOADING = "アップロード中..."

/**
 * バックグラウンドでのデータ処理（アップロード、削除等）中の確定進捗表示用インジケータ。
 *
 * 不確定な待機状態を完全に排除し、呼び出し元から渡された全体件数（分母）に基づいて
 * 常に確定的な進捗バー (current / total) とパーセンテージを表示します。
 *
 * @param uploadProgress 現在の進捗状況を表す Progress オブジェクト。非 Null。
 * @param label 進捗テキストに表示する処理名称（例: "アップロード中...", "削除中..."）。呼び出し元で明確に指定すること。
 * @param onCancel キャンセルボタン押下時に呼び出されるコールバック。
 */
@Composable
fun UploadIndicator(
    uploadProgress: Progress,
    label: String,
    onCancel: (() -> Unit),
) {
    // 背景を半透明にして背面クリックを無効化する
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(color = Color.Black.copy(alpha = 0.4f))
                .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.padding(horizontal = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                Text(
                    text = "${uploadProgress.progressed} / ${uploadProgress.total} $label (${uploadProgress.percent}%)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { uploadProgress.fraction },
                    modifier = Modifier.width(240.dp).height(8.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { onCancel.invoke() }) {
                    Text(text = "強制キャンセル")
                }
            }
        }
    }
}
