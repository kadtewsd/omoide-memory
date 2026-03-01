package com.kasakaid.omoidememory.downloader.domain

import arrow.core.Either
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.domain.OmoideMemory
import java.nio.file.Path

/**
 * DIP にしたがってドライブのアクセスの実装は切り離す
 */
interface DriveService {
    /**
     * ドライブからファイルを持ってきます。
     * 実装は Google や OneDrive などいずれかのドライブのアクセスになります。
     * まずはメタデータを取得します。
     */
    suspend fun listFiles(): List<File>

    /**
     * 取得されたファイルのメタデータから実体を取得してメモリをローカル PC のストレージに書き込みます。
     * omoideBackupPath -> コンテンツをバックアップするディレクトリ
     */
    class WriteError(
        val paths: Set<Path>,
    ) {
        constructor(
            path: Path,
        ) : this(paths = setOf(path))
    }

    suspend fun writeOmoideMemoryToTargetPath(
        googleFile: File,
        omoideBackupPath: Path,
        mediaType: MediaType,
        familyId: String,
    ): Either<WriteError, OmoideMemory>

    /**
     * 指定ファイルをゴミ箱に移動
     * 内部で setTrashed(true) を使用すること
     * delete() は使用禁止（DB保存失敗時に完全消去されるため）
     *
     * @param fileId Google Drive ファイルID
     * @return 成功時は Unit、失敗時は Throwable を Either 型で返す
     */
    suspend fun moveToTrash(fileId: String): Either<Throwable, Unit>
}
