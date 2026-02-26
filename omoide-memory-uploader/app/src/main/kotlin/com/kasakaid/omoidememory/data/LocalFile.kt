package com.kasakaid.omoidememory.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ストレージから取り出してきたもの
 * data class にすると遅いので class だけにします。
 */
@Entity(tableName = "uploaded_memories")
class LocalFile(
    @PrimaryKey val id: Int,
    val name: String,
    val filePath: String?,
    val fileSize: Long?,
    val mimeType: String?,
    val dateModified: Long?,
    val dateTaken: Long?,
    // 回転情報
    val orientation: Int?,
    // Roomが再生成時にもセットできるようにコンストラクタに含める
    // Google Drive File ID。アップロードが完了するまで NULL をいれておく。。。別の LocalFileWithHash のようなオブジェクトを作ると不自然のため
    var driveFileId: String? = null,
) {
    fun onUploaded(uploadedDriveFileId: String): LocalFile =
        apply {
            driveFileId = uploadedDriveFileId
        }
}
