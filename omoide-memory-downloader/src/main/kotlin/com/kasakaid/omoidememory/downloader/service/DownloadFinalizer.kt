package com.kasakaid.omoidememory.downloader.service

import arrow.core.Either
import com.kasakaid.omoidememory.downloader.adapter.google.RefreshToken

/**
 * ダウンロード後の後処理を行うインターフェース
 */
interface DownloadFinalizer {
    suspend fun finalize(
        fileId: String,
        token: RefreshToken,
    ): Either<Throwable, Unit>
}
