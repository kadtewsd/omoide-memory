package com.kasakaid.omoidememory.domain

import arrow.core.Either
import arrow.core.raise.context.bind
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import com.kasakaid.omoidememory.downloader.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.regex.Pattern

object OmoideMemoryMetadataService {
    /**
     * ローカルファイルからメタデータを抽出してOmoideMemoryを生成
     * Google Driveには関係しないため、driveFileIdはnull
     */
    suspend fun extractOmoideMemoryFromLocalFile(
        localFile: LocalFile,
        mediaType: MediaType,
    ): Either<MetadataExtractError, OmoideMemory> =
        withContext(Dispatchers.IO) {
            either {
                val path = localFile.validate().bind()
                // SourceFile を作成（driveFileId = null）
                val sourceFile = SourceFile.fromLocalFile(path)

                // メタデータを抽出
                mediaType
                    .createMediaMetadata(localFile)
                    .toMedia(sourceFile)
                    .mapLeft {
                        raise(MetadataExtractError(it.ex))
                    }.bind()
                    .fixCaptureTime(
                        estimateCaptureTime(path),
                    )
            }
        }

    /**
     * 撮影日時を推測する
     * 優先順位: 1.EXIF/メタデータ → 2.ファイル名 → 3.ディレクトリ構造 → 4.ファイル最終更新日時
     */
    private fun estimateCaptureTime(filePath: Path): OffsetDateTime {
        // 1. ファイル名から推測
        FileOrganizeService.extractDateFromFilename(filePath.fileName.toString())?.let { return it }

        // 2. ディレクトリ構造から推測
        extractDateFromDirectory(filePath)?.let { return it }

        // 3. ファイルの最終更新日時
        return OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(Files.getLastModifiedTime(filePath).toMillis()),
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
