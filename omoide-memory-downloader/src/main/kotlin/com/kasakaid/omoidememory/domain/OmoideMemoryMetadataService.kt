package com.kasakaid.omoidememory.domain

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
import kotlin.io.path.isRegularFile

@Service
class OmoideMemoryMetadataService {
    private val dateInFilenamePattern = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})")

    /**
     * ローカルファイルからメタデータを抽出してOmoideMemoryを生成
     * Google Driveには関係しないため、driveFileIdはnull
     */
    suspend fun extractOmoideMemoryFromLocalFile(
        localFile: LocalFile,
        mediaType: MediaType,
    ): OmoideMemory =
        withContext(Dispatchers.IO) {
            val filePath = localFile.path
            require(filePath.isRegularFile()) { "ファイルが存在しません: $filePath" }

            // SourceFile を作成（driveFileId = null）
            val sourceFile = SourceFile.fromLocalFile(filePath)

            // メタデータを抽出
            val metadata = mediaType.createMediaMetadata(localFile)
            val result = metadata.toMedia(sourceFile)

            val omoideMemory =
                result.fold(
                    ifLeft = { throw RuntimeException("メタデータ抽出失敗", it.ex) },
                    ifRight = { it },
                )

            // EXIF等のメタデータに撮影時刻が存在しない場合は推測する
            if (omoideMemory.captureTime == null) {
                val estimatedTime = estimateCaptureTime(filePath)
                when (omoideMemory) {
                    is OmoideMemory.Photo -> {
                        OmoideMemory.Photo(
                            localPath = omoideMemory.localPath,
                            name = omoideMemory.name,
                            mediaType = omoideMemory.mediaType,
                            driveFileId = omoideMemory.driveFileId,
                            fileSize = omoideMemory.fileSize,
                            locationName = omoideMemory.locationName,
                            aperture = omoideMemory.aperture,
                            shutterSpeed = omoideMemory.shutterSpeed,
                            isoSpeed = omoideMemory.isoSpeed,
                            focalLength = omoideMemory.focalLength,
                            focalLength35mm = omoideMemory.focalLength35mm,
                            whiteBalance = omoideMemory.whiteBalance,
                            imageWidth = omoideMemory.imageWidth,
                            imageHeight = omoideMemory.imageHeight,
                            orientation = omoideMemory.orientation,
                            latitude = omoideMemory.latitude,
                            longitude = omoideMemory.longitude,
                            altitude = omoideMemory.altitude,
                            captureTime = estimatedTime,
                            deviceMake = omoideMemory.deviceMake,
                            deviceModel = omoideMemory.deviceModel,
                        )
                    }

                    is OmoideMemory.Video -> {
                        OmoideMemory.Video(
                            localPath = omoideMemory.localPath,
                            name = omoideMemory.name,
                            mediaType = omoideMemory.mediaType,
                            driveFileId = omoideMemory.driveFileId,
                            fileSize = omoideMemory.fileSize,
                            metadata = omoideMemory.metadata,
                            captureTime = estimatedTime,
                        )
                    }
                }
            } else {
                omoideMemory
            }
        }

    private fun extractDateFromFilename(filename: String): OffsetDateTime? {
        val matcher = dateInFilenamePattern.matcher(filename)
        if (matcher.find()) {
            try {
                val year = matcher.group(1).toInt()
                val month = matcher.group(2).toInt()
                val day = matcher.group(3).toInt()
                return LocalDateTime
                    .of(year, month, day, 12, 0)
                    .atZone(ZoneId.systemDefault())
                    .toOffsetDateTime()
            } catch (e: Exception) {
            }
        }
        return null
    }

    /**
     * 撮影日時を推測する
     * 優先順位: 1.EXIF/メタデータ → 2.ファイル名 → 3.ディレクトリ構造 → 4.ファイル最終更新日時
     */
    private fun estimateCaptureTime(filePath: Path): OffsetDateTime? {
        // 2. ファイル名から推測
        extractDateFromFilename(filePath.fileName.toString())?.let { return it }

        // 3. ディレクトリ構造から推測
        extractDateFromDirectory(filePath)?.let { return it }

        // 4. ファイルの最終更新日時
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

        return if (year != null && month != null) {
            try {
                LocalDateTime
                    .of(year, month, 1, 12, 0)
                    .atZone(ZoneId.systemDefault())
                    .toOffsetDateTime()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}
