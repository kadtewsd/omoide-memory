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
     * ダウンロード完了後の後処理を実行します。
     * 実装ドライブによって、フラグの付与やファイルのゴミ箱への移動などの異なる後処理を行います。
     *
     * @param fileId 後処理の対象となる Google Drive 上のファイル ID
     * @param accessInfo SA（Service Account）の場合は folderId、Refresh Token の場合は refreshToken
     * @return 処理結果を表す Either。成功した場合は Unit、失敗した場合は例外 Throwable が Left に入る
     */
    suspend fun finalize(
        fileId: String,
        accessInfo: String,
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
