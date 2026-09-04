package com.kasakaid.omoidememory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

const val CONTENTS_UPLOADING = "アップロード中..."

/**
 * バックグラウンドでのデータ処理（アップロード、削除等）中の進捗および画面ロック用インジケータ。
 *
 * @param uploadProgress 現在の進捗件数と全体の件数の Pair (current, total)。null の場合は準備中として表示。
 * @param label 進捗テキストの末尾に表示する処理名称（例: "アップロード中...", "削除中..."）。呼び出し元で明確に指定すること。
 * @param onCancel キャンセルボタン押下時に呼び出されるコールバック。
 */
@Composable
fun UploadIndicator(
    uploadProgress: Pair<Int, Int>?,
    label: String,
    onCancel: (() -> Unit),
) {
    // 背景を少し白くして、クリックを無効化する
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.7f)).pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                uploadProgress != null && uploadProgress.second > 0 -> {
                    val (current, total) = uploadProgress
                    LinearProgressIndicator(
                        progress = { (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.width(200.dp),
                    )
                    Text("$current / $total $label")
                }

                else -> {
                    // まだ起動待ちの時はグルグル
                    CircularProgressIndicator()
                    Text("準備中...")
                }
            }

            Spacer(modifier = Modifier.padding(16.dp))
            Button(onClick = { onCancel.invoke() }) {
                Text("強制キャンセル")
            }
        }
    }
}
