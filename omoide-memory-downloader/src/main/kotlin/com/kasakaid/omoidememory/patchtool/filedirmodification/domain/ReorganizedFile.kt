package com.kasakaid.omoidememory.patchtool.filedirmodification.domain

import com.kasakaid.omoidememory.downloader.domain.MediaType
import java.nio.file.Path
import java.time.OffsetDateTime

class ReorganizedFile(
    val fileName: String,
    val mediaType: MediaType,
    val captureTime: OffsetDateTime,
    val backupRootPath: Path,
) {
    val correctPath: Path by lazy {
        val year = captureTime.year.toString()
        val month = String.format("%02d", captureTime.monthValue)
        backupRootPath
            .resolve(year)
            .resolve(month)
            .resolve(mediaType.directoryName)
            .resolve(fileName)
    }

    val serverPathString: String
        get() = correctPath.toAbsolutePath().toString()
}
