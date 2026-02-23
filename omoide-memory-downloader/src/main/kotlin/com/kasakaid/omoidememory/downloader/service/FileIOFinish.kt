package com.kasakaid.omoidememory.downloader.service

import java.nio.file.Path

sealed interface FileIOFinish {
    val filePath: Path

    class Success(
        override val filePath: Path,
    ) : FileIOFinish

    class Skip(
        val reason: String,
        override val filePath: Path,
    ) : FileIOFinish
}
