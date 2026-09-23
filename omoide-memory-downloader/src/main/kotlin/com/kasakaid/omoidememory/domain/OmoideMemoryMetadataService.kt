package com.kasakaid.omoidememory.domain

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

object OmoideMemoryMetadataService {
    /**
     * 撮影日時を推測する
     * 優先順位: 1.EXIF/メタデータ → 2.ファイル名 → 3.ディレクトリ構造 → 4.ファイル最終更新日時
     */
    fun estimateCaptureTimeFrom(filePath: Path): OffsetDateTime {
        // 1. ファイル名から推測
        extractDateFromFilename(filePath.fileName.toString())
            .getOrNull()
            ?.let { return it }

        // 2. ディレクトリ構造から推測
        extractDateFromDirectory(filePath)?.let { return it }

        // 3. ファイルの最終更新日時
        return OffsetDateTime.ofInstant(
            Files.getLastModifiedTime(filePath).toInstant(),
            ZoneId.systemDefault(),
        )
    }

    /**
     * ディレクトリ構造から日付を推測
     * 例: /path/to/2023/05/photo/image.jpg → 2023-05-01
     */
    private fun extractDateFromDirectory(filePath: Path): OffsetDateTime? {
        val parts = filePath.toAbsolutePath().toString().split(File.separator)

        var year: Int? = null
        var month: Int? = null

        for (i in parts.indices) {
            val part = parts[i]
            if (part.matches(Regex("\\d{4}"))) {
                year = part.toIntOrNull()
                if (i + 1 < parts.size) {
                    val nextPart = parts[i + 1]
                    if (nextPart.matches(Regex("\\d{2}"))) {
                        month = nextPart.toIntOrNull()
                        break
                    }
                }
            }
        }
        return if (year == null || month == null) {
            null
        } else {
            LocalDateTime
                .of(
                    year,
                    month,
                    1,
                    12,
                    0,
                ).atZone(ZoneId.systemDefault())
                .toOffsetDateTime()
        }
    }
}
