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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    @OptIn(ExperimentalCoroutinesApi::class)
    val potentialPendingFiles: Flow<List<LocalFile>> = run {
        // MediaStore から名前・サイズ・パスを取得
        // Room から「アップロード済みメタデータ一覧」を取得して、名前・サイズで簡易フィルタ
        // 1. 最初の一回だけ DB から全ハッシュをロードして Set にする
        omoideMemoryDao.getAllUploadedNames()
            .map { it.toSet() } // 🚀 ここで「川」の中で Set に変換
            .flatMapLatest { uploadedNameSet ->
                getPendingFiles { file ->
                    file.takeIf { !uploadedNameSet.contains(it.name) }.toOption()
                }
            }
    }
    fun <T> getPendingFiles(
        filterUnuploadedFile: (LocalFile) -> Option<T>,
    ): Flow<List<T>> = channelFlow {
        // channelFlow の中は、デフォルトで適切なスコープで動くので
        // そのまま IO 処理を書いて OK です
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

        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            (omoideUploadPrefsRepository.getUploadBaseLineInstant()?.epochSecond ?: 0L).toString()
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val currentList = mutableListOf<T>()

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
                    filterUnuploadedFile(localFile).fold(
                        { /* None の場合は何もしない */ },
                        { item ->
                            currentList.add(item)
                            // 🚀 20件ごとに最新のリストを「川」に流す（非同期描画！）
                            // 「少しずつ流す」ためには、今までの「一括で変換して最後にリストを返す」という書き方から、「1件ずつ調べて、溜まったら送る」という書き方に変える必要があります。
                            // .map{...}.toList() のような書き方だと、最後の1件の処理が終わるまで結果が確定しない（＝川に流せない） からです。
                            if (currentList.size % 20 == 0) {
                                // send で川を流しながら、たまったものを送りつける、というようなことができる
                                send(currentList.toList())
                            }
                        }
                    )
                }
            }
        }
        // 最後に全件入ったリストを流して完了
        send(currentList.toList())
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
