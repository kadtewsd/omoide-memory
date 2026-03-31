package com.kasakaid.omoidememory.patchtool.filedirmodification.domain

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import com.kasakaid.omoidememory.domain.extractDateFromFilename
import com.kasakaid.omoidememory.downloader.domain.MediaType
import java.nio.file.Path
import java.time.OffsetDateTime

class ReorganizedFile(
    val filePath: Path,
    private val backupRootPath: Path,
) {
    val fileName: String = filePath.fileName.toString()
    val mediaType: MediaType? = MediaType.of(fileName).getOrNull()
    val captureTime: OffsetDateTime? = extractDateFromFilename(fileName).getOrNull()

    val isProcessable: Boolean
        get() = mediaType != null && captureTime != null

    fun correctByFileName(): Option<ReorganizedFile> {
        if (!isProcessable) return None

        val year = captureTime!!.year.toString()
        val month = String.format("%02d", captureTime.monthValue)
        val correctPath =
            backupRootPath
                .resolve(year)
                .resolve(month)
                .resolve(mediaType!!.directoryName)
                .resolve(fileName)

        return if (filePath.toAbsolutePath() != correctPath.toAbsolutePath()) {
            ReorganizedFile(correctPath, backupRootPath).some()
        } else {
            None
        }
    }
}
