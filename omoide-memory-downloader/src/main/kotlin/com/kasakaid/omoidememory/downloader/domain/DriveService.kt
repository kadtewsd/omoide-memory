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
    ): Either<WriteError, OmoideMemory>

    class FileDeleteError(
        val throwable: Throwable,
    )
}
