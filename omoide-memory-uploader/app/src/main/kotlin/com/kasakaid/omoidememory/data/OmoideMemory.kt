package com.kasakaid.omoidememory.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kasakaid.omoidememory.ui.EnumWithLabel

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
    @androidx.room.ColumnInfo(defaultValue = "DONE")
    var state: UploadState = UploadState.DONE,
) {
    fun done(): OmoideMemory =
        apply {
            this.state = UploadState.DONE
        }

    fun exclude(): OmoideMemory =
        apply {
            this.state = UploadState.EXCLUDED
        }

    fun driveDeleted(): OmoideMemory =
        apply {
            this.state = UploadState.DRIVE_DELETED
        }

    companion object {
        const val UPLOAD_LIMIT_BYTES = 10 * 1024 * 1024 * 1024L
    }
}

fun List<OmoideMemory>.totalSize(): Long = sumOf { it.fileSize ?: 0L }

fun List<OmoideMemory>.isOverLimit(): Boolean = totalSize() > OmoideMemory.UPLOAD_LIMIT_BYTES

enum class UploadState(
    override val label: String,
) : EnumWithLabel {
    READY("アップロード待ち"),
    UPLOADING("実行中"),
    DONE("完了"),
    FAILED("失敗"),
    EXCLUDED("除外"),
    DRIVE_DELETED("ドライブ削除済み"),
}

class ExcludeOmoide(
    val id: Long,
)
