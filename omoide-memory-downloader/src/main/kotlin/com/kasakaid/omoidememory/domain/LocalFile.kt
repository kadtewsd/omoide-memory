package com.kasakaid.omoidememory.domain

import arrow.core.Either
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * ローカルファイルを表すドメインモデル
 */
class LocalFile(
    val path: Path,
    val name: String,
) {
    fun validate(): Either<MetadataExtractError, Path> =
        when (path.isRegularFile()) {
            true -> path
            false -> MetadataExtractError(IllegalStateException("ファイルが存在しません"))
        }
}
