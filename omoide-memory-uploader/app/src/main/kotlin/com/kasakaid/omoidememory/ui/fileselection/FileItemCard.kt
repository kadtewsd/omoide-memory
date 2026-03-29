package com.kasakaid.omoidememory.ui.fileselection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.kasakaid.omoidememory.data.OmoideMemory

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemCard(
    item: OmoideMemory,
    imageLoader: coil.ImageLoader,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
) {
    /**
     * 動画（video）かどうかを判定。
     * 動画の場合は「再生ボタン」の表示や「長押しでのプレビュー」を有効にします。
     */
    val isVideo = item.mimeType?.startsWith("video") == true

    // 選択状態に応じた色の定義
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderStroke = if (isSelected) 3.dp else 0.dp

    // カード全体の枠組み
    Box(
        modifier =
            Modifier
                .padding(4.dp)
                .aspectRatio(1f) // 正方形に
                .border(borderStroke, borderColor, RoundedCornerShape(8.dp)) // 選択された時に青い枠を表示
                .clip(RoundedCornerShape(8.dp))
                /**
                 * [combinedClickable] を使う理由：
                 * 1つの動作（クリック）だけではなく、複数の操作を使い分けるためです。
                 * 1. onClick (普通のタップ): ファイルの「選択 / 解除」を行う（以前と同じ動作）。
                 * 2. onLongClick (長押し): 動画の場合のみ、その場で中身を確認できるプレビューダイアログを開く。
                 */
                .combinedClickable(
                    onClick = { onToggle() },
                    onLongClick = { if (isVideo) onPreview() },
                ),
    ) {
        // 画像 / 動画のサムネイル表示
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(item.filePath)
                    /**
                     * 🚀 サムネイルとして「動画の0.5秒目」の画像を取得しています。
                     * 1秒だと真っ黒な画面になる端末があるため、少し手前を指定して回避しています。
                     */
                    .videoFrameMillis(500)
                    .crossfade(true)
                    .build(),
            imageLoader = imageLoader,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .alpha(if (isSelected) 1f else 0.8f),
            contentScale = ContentScale.Crop,
        )

        /**
         * 動画の場合のみ、中央に「再生ボタン」のアイコンを重ねます。
         * これにより、ユーザーが「あ、これは動画なんだな」と一目でわかります。
         */
        if (isVideo) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "動画をプレビュー",
                tint = Color.White.copy(alpha = 0.8f),
                modifier =
                    Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                        /**
                         * 長押しだけでなく、このボタンを直接タップしてもプレビューが開くようにして
                         * 初めて使う人にも「動画を見れる」ことを伝えやすくしています。
                         */
                        .clickable { onPreview() },
            )
        }

        // 選択中かどうかを示す右上のチェックボックス
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}
