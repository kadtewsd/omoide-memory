package com.kasakaid.omoidememory.ui.fileselection

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.kasakaid.omoidememory.data.OmoideMemory

@Composable
fun VideoPreviewDialog(
    item: OmoideMemory,
    onDismissRequest: () -> Unit,
) {
    val uri =
        remember(item) {
            val baseUri =
                if (item.mimeType?.startsWith("video") == true) {
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            android.content.ContentUris.withAppendedId(baseUri, item.id)
        }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            shape = RoundedCornerShape(16.dp),
        ) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    // 必要に応じてアップデート
                },
            )
        }
    }
}

