package com.kasakaid.omoidememory.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import arrow.core.Option
import arrow.core.toOption
import com.kasakaid.omoidememory.extension.CursorExtension.asSequence
import com.kasakaid.omoidememory.extension.flattenOption
import com.kasakaid.omoidememory.os.FolderUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 端末に存在するファイルを取得します。
 */
@Singleton
class LocalFileRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val omoideMemoryDao: OmoideMemoryDao,
    private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
) {

    // 1. メタデータだけで「たぶん未アップロード」なものをガバッと取る（高速）
    suspend fun getPotentialPendingFiles(): List<LocalFile> {
        // MediaStore から名前・サイズ・パスを取得
        // Room から「アップロード済みメタデータ一覧」を取得して、名前・サイズで簡易フィルタ
        // 1. 最初の一回だけ DB から全ハッシュをロードして Set にする
        val uploadedNameSet = omoideMemoryDao.getAllUploadedNames().toSet()
        return getPendingFiles { file ->
            file.takeIf { !uploadedNameSet.contains(it.name) }.toOption()
        }
    }

    suspend fun <T> getPendingFiles(
        filterUnuploadedFile: (LocalFile) -> Option<T>,
    ): List<T> = withContext(Dispatchers.IO) {

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATA
        )

        // 2. Selection に日付の条件を追加
        // MEDIA_TYPE の判定に加え、DATE_ADDED が基準日以上のものを指定
        val selection = """
        (${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)
        AND ${MediaStore.Files.FileColumns.DATE_ADDED} >= ?
    """.trimIndent()

        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            // 基準日の取得と秒への変換。SMediaStoreは「秒」なので、InstantからepochSecondを取得。tringとして渡す
            (omoideUploadPrefsRepository.getUploadBaseLineInstant()?.epochSecond ?: 0L).toString()
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            FolderUri.content,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            cursor.asSequence()
                .map {
                    LocalFile(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)),
                        filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)),
                        fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)),
                        mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)),
                    )
                }.filter {
                    // Skip if null
                    it.filePath != null
                }.map {
                    // 3. メモリ上の Set で高速照合
                    filterUnuploadedFile(it)
                }.flattenOption()
                .toList()
        } ?: emptyList()
    }
}

/**
 * ストレージから取り出してきたもの
 */
data class LocalFile(
    val id: Long?,
    val name: String?,
    val filePath: String?,
    val fileSize: Long?,
    val mimeType: String?,
) {
    fun getContentUri(collection: Uri): Uri = Uri.withAppendedPath(collection, id.toString())
}
