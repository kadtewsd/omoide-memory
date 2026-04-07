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
     * 戻り値はファイルリストです。
     *
     * @param accessInfo SA の場合は folderId, RefreshToken の場合は refreshToken
     */
    suspend fun listFiles(accessInfo: String): List<File>

    /**
     * ファイルをダウンロードして OutputStream に書き込みます。
     */
    suspend fun download(
        fileId: String,
        outputStream: java.io.OutputStream,
    ): Either<Throwable, Unit>

    /**
     * 取得されたファイルのメタデータから実体を取得してメモリをローカル PC のストレージに書き込みます。
     * omoideBackupPath -> コンテンツをバックアップするディレクトリ
     */
    data class WriteError(
        val paths: Set<Path>,
    ) {
        constructor(
            path: Path,
        ) : this(paths = setOf(path))
    }
}
