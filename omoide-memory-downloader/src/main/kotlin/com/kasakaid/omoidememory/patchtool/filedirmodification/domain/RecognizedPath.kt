package com.kasakaid.omoidememory.patchtool.filedirmodification.domain

import com.kasakaid.omoidememory.domain.FileOrganizeService
import com.kasakaid.omoidememory.downloader.domain.MediaType
import java.nio.file.Path

class RecognizedPath(
    val path: Path,
) {
    val fileName: String = path.fileName.toString()

    val mediaType: MediaType? = MediaType.of(fileName).getOrNull()

    val correctCaptureTime = FileOrganizeService.extractDateFromFilename(fileName)

    val isProcessable: Boolean
        get() = mediaType != null && correctCaptureTime != null
}
