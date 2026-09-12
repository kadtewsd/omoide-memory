package com.kasakaid.omoidememory.ui.indicator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.kasakaid.omoidememory.ui.indicator.Progress
import com.kasakaid.omoidememory.worker.GoogleDriveRequestType

const val CONTENTS_UPLOADING = "アップロード中..."

/**
 * 画面上部に表示されるワーカー処理進捗バー（キャンセルボタン付き）。
 */
@Composable
fun WorkProgressTopCard(
    request: GoogleDriveRequestType.Processing,
    modifier: Modifier = Modifier,
) {
    WorkProgressTopCard(
        uploadProgress = request.requestProgress,
        label = request.label,
        onCancel = { request.onCancel() },
        modifier = modifier,
    )
}

/**
 * 画面上部に表示されるワーカー処理進捗バー（キャンセルボタン付き）。
 */
@Composable
fun WorkProgressTopCard(
    uploadProgress: Progress,
    label: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$label: ${uploadProgress.progressed} / ${uploadProgress.total} (${uploadProgress.percent}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Button(
                    onClick = onCancel,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("キャンセル", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { uploadProgress.fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

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
