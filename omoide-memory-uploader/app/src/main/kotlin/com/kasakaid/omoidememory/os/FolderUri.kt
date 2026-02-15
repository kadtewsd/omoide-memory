package com.kasakaid.omoidememory.os

import android.net.Uri
import android.provider.MediaStore

/**
 * フォルダーに関する Uri
 */
object FolderUri {
    /**
     * コンテンツがある Uri
     */
    val content: Uri =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
}