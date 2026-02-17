package com.kasakaid.omoidememory.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import arrow.core.Option
import arrow.core.toOption
import com.kasakaid.omoidememory.extension.CursorExtension.asSequence
import com.kasakaid.omoidememory.extension.toLocalDateTime
import com.kasakaid.omoidememory.os.FolderUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Instant
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

    companion object {
        val TAG = "LocalFileRepository"
    }

    // 1. メタデータだけで「たぶん未アップロード」なものをガバッと取る（高速）
    fun getPotentialPendingFiles(): Flow<LocalFile> = flow {
        // MediaStore から名前・サイズ・パスを取得
        // Room から「アップロード済みメタデータ一覧」を取得して、名前・サイズで簡易フィルタ
        // 1. 最初の一回だけ DB から全ハッシュをロードして Set にする
        val uploadedNameSet = omoideMemoryDao.getAllUploadedNames().toSet()
        getPendingFiles { file ->
            file.takeIf { !uploadedNameSet.contains(it.name) }.toOption()
        }.let {
            emitAll(it)
        }
    }

    /**
     * 見つかったら一つづつちょろちょろと川を流して呼び出し元に教えて (send) してあげる
     */
    fun <T> getPendingFiles(
        filterUnuploadedFile: (LocalFile) -> Option<T>,
    ): Flow<T> = channelFlow {
        // channelFlow の中は、デフォルトで適切なスコープで動くので
        // そのまま IO 処理を書いて OK です
        Log.d(TAG, "指定されたフィルタでアップロード候補のファイルを取得します。")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATA
        )

        val selection = """
        (${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)
        AND ${MediaStore.Files.FileColumns.DATE_ADDED} >= ?
    """.trimIndent()

        val baseline: Instant? = omoideUploadPrefsRepository.getUploadBaseLineInstant().first()
        Log.d(TAG, "基準日 : ${baseline?.toLocalDateTime()} で検索開始")
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            (baseline?.epochSecond ?: 0L).toString(),
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            FolderUri.content,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            // sequence として 1 件ずつ処理
            cursor.asSequence().forEach { _ ->
                val localFile = LocalFile(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)),
                    filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)),
                    fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)),
                    mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)),
                )

                if (localFile.filePath != null) {
                    // 未アップロード判定を通ったものだけ currentList に追加
                    filterUnuploadedFile(localFile).onSome { item ->
                        send(item)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO) // 🚀 これを付けておけば、どこで呼んでも安全
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
