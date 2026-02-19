package com.kasakaid.omoidememory.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "uploaded_memories")
class OmoideMemory(
    @PrimaryKey val hash: String, // Hash of the file content
    val id: String,
    val name: String,
    val filePath: String,
    val fileSize: Long,
    val uploadedAt: Long,
    val mimeType: String,
    // Roomが再生成時にもセットできるようにコンストラクタに含める
    // Google Drive File ID。アップロードが完了するまで NULL をいれておく。。。別の LocalFileWithHash のようなオブジェクトを作ると不自然のため
    var driveFileId: String? = null,
    val dateModified: Long?,
    val dateTaken: Long?,
    // 回転情報
    val orientation: Int?,
) {
    companion object {
        fun of(
            localFile: LocalFile, hash: String): OmoideMemory = localFile.run {
            OmoideMemory(
                hash = hash,
                id = id.toString(),
                name = name!!,
                filePath = filePath!!,
                fileSize = fileSize!!,
                uploadedAt = System.currentTimeMillis(),
                mimeType = mimeType!!,
                dateModified = dateModified,
                dateTaken = dateTaken,
                orientation = orientation,
            )
        }
    }
    fun onUploaded(uploadedDriveFileId: String): OmoideMemory = apply {
        driveFileId = uploadedDriveFileId
    }
}
