package com.kasakaid.pictureuploader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "uploaded_memories")
data class OmoideMemory(
    @PrimaryKey val id: String, // Hash of the file content
    val filePath: String,
    val fileSize: Long,
    val uploadedAt: Long,
    val mimeType: String,
    val driveFileId: String? = null // Google Drive File ID
)
