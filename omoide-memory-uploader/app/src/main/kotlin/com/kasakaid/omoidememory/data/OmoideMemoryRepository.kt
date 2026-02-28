package com.kasakaid.omoidememory.data

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.right
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
class OmoideMemoryRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val omoideMemoryDao: OmoideMemoryDao,
        private val omoideUploadPrefsRepository: OmoideUploadPrefsRepository,
    ) {
        companion object {
            val TAG = "LocalFileRepository"
        }

        // 1. メタデータだけで「たぶん未アップロード」なものをガバッと取る（高速）
        fun getPotentialPendingFiles(): Flow<OmoideMemory> =
            flow {
                // MediaStore から名前・サイズ・パスを取得
                // Room から「アップロード済みメタデータ一覧」を取得して、名前・サイズで簡易フィルタ
                // 1. 最初の一回だけ DB から全ハッシュをロードして Set にする
                val uploadedNameSet = omoideMemoryDao.getAllUploadedIds().toSet()
                getPendingFiles { file ->
                    file.takeIf { !uploadedNameSet.contains(it.id) }.toOption()
                }.let {
                    emitAll(it)
                }
            }

        class PathNoneError(
            val name: String?,
        )

        // 2. 取得処理
        private fun Cursor.toOmoideMemory(): Either<PathNoneError, OmoideMemory> {
            // getColumnIndex は存在しないと -1 を返す
            val nameIdx = getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val pathIdx = getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val sizeIdx = getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val mimeIdx = getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)

            // 2. 値の取得とフォールバック
            val addedIdx = getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateAdded = if (addedIdx != -1) getLong(addedIdx) * 1000L else null
            val modifiedIdx = getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val modTime = if (modifiedIdx != -1) getLong(modifiedIdx) * 1000L else dateAdded
            // 画像/動画特有のカラム（Files テーブルでも内部的に持っていることが多い）
            val takenIdx = getColumnIndex("datetaken") // 画像以外は存在しないことが多い
            val orientationIdx = getColumnIndex("orientation")

            val name = if (nameIdx != -1) getString(nameIdx) ?: "unknown" else "unknown"
            return if (pathIdx == -1 || getString(pathIdx).isNullOrEmpty()) {
                PathNoneError(name).left()
            } else {
                OmoideMemory(
                    id = getLong(getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)),
                    name = name,
                    filePath = getString(pathIdx)!!,
                    fileSize = if (sizeIdx != -1) getLong(sizeIdx) else 0L,
                    mimeType =
                        if (mimeIdx != -1) {
                            getString(mimeIdx)
                                ?: "application/octet-stream"
                        } else {
                            "application/octet-stream"
                        },
                    dateModified = modTime,
                    // 日時系：取れない場合は date_added や 現在時刻でフォールバック
                    // dateTaken: 無ければ null
                    dateTaken =
                        when {
                            takenIdx != -1 && !isNull(takenIdx) -> getLong(takenIdx)
                            else -> null
                        },
                    // orientation: 無ければ null
                    orientation = if (orientationIdx != -1) getInt(orientationIdx) else null,
                ).right()
            }
        }

        /**
         * 見つかったら一つづつちょろちょろと川を流して呼び出し元に教えて (send) してあげる
         */
        private fun <T> getPendingFiles(filterUnuploadedFile: (OmoideMemory) -> Option<T>): Flow<T> =
            channelFlow {
                // channelFlow の中は、デフォルトで適切なスコープで動くので
                // そのまま IO 処理を書いて OK です
                Log.d(TAG, "指定されたフィルタでアップロード候補のファイルを取得します。")

                val selection =
                    """
                    (${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)
                    AND ${MediaStore.Files.FileColumns.DATE_ADDED} >= ?
                    """.trimIndent()

                val baseline: Instant? = omoideUploadPrefsRepository.getUploadBaseLineInstant().first()
                Log.d(TAG, "基準日 : ${baseline?.toLocalDateTime()} で検索開始")
                val selectionArgs =
                    arrayOf(
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                            .toString(),
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                            .toString(),
                        (baseline?.epochSecond ?: 0L).toString(),
                    )

                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

                context.contentResolver
                    .query(
                        FolderUri.content,
                        arrayOf(
                            MediaStore.Files.FileColumns._ID,
                            MediaStore.Files.FileColumns.DISPLAY_NAME,
                            MediaStore.Files.FileColumns.SIZE,
                            MediaStore.Files.FileColumns.MIME_TYPE,
                            MediaStore.Files.FileColumns.DATE_ADDED,
                            MediaStore.Files.FileColumns.DATA,
                            MediaStore.Files.FileColumns.DATE_ADDED, // 追加
                            MediaStore.Files.FileColumns.DATE_MODIFIED, // 追加
                            "datetaken", // MediaStore.Images.Media.DATE_TAKEN だが文字列で指定した方が安全な場合がある
                        ), // Projection
                        selection,
                        selectionArgs,
                        sortOrder,
                    )?.use { cursor ->
                        // sequence として 1 件ずつ処理
                        cursor.asSequence().forEach { _ ->
                            cursor.toOmoideMemory().fold(
                                ifLeft = {
                                    Log.i(TAG, "${it.name}のパスが取得できないのでスキップ")
                                },
                                ifRight = { localFile ->
                                    // 未アップロード判定を通ったものだけ currentList に追加
                                    filterUnuploadedFile(localFile).onSome { item ->
                                        Log.d(TAG, "${localFile.name}未アップロード判定")
                                        send(item)
                                    }
                                },
                            )
                        }
                    }
            }.flowOn(Dispatchers.IO) // 🚀 これを付けておけば、どこで呼んでも安全

        /**
         * すでにアップロードされたコンテンツの数を取得
         */
        fun getUploadedCount(): Flow<Int> = omoideMemoryDao.getUploadedCount()

        suspend fun markAsUploaded(entity: OmoideMemory) {
            omoideMemoryDao.insertUploadedFile(entity)
        }

        suspend fun findReadyForUpload(): List<OmoideMemory> = omoideMemoryDao.findReadyForUpload()

        suspend fun markAsReady(entities: List<OmoideMemory>) = omoideMemoryDao.insertUploadedFiles(entities)

        suspend fun markAsDone(
            id: Long,
            driveFileId: String,
        ) = omoideMemoryDao.markAsDone(id, driveFileId)

        suspend fun clearReadyFiles() = omoideMemoryDao.deleteReadyFiles()
    }
