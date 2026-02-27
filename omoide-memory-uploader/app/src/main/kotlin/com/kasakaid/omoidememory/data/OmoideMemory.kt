package com.kasakaid.omoidememory.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ストレージから取り出してきたもの
 * data class にすると遅いので class だけにします。
 */
@Entity(tableName = "uploaded_memories")
class OmoideMemory(
    @PrimaryKey val id: Long,
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
    @androidx.room.ColumnInfo(defaultValue = "DONE")
    var state: UploadState = UploadState.DONE,
)

enum class UploadState {
    READY, // アップロード待ち
    UPLOADING, // 実行中（任意）
    DONE, // 完了
    FAILED, // 失敗（任意）
}
