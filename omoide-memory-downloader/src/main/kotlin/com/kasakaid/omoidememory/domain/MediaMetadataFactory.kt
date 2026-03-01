package com.kasakaid.omoidememory.domain

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object MediaMetadataFactory {
    fun createVideo(localFile: LocalFile): VideoMetadata {
        val captured =
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(Files.getLastModifiedTime(localFile.path).toMillis()),
                ZoneId.systemDefault(),
            )
        return VideoMetadata(capturedTime = captured, filePath = localFile.path)
    }

    fun createPhoto(localFile: Path): PhotoMetadata {
        val metadata = ImageMetadataReader.readMetadata(localFile.toFile())

        val exifIFD0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val exifSubIFD = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        val gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)

        val captured =
            exifSubIFD
                ?.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                ?.let { raw ->
                    // "2026:02:28 23:10:52" 形式をLocalDateTimeとして直接パース
                    LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                }?.let { localDateTime ->
                    OffsetDateTime.of(localDateTime, ZoneOffset.ofHours(9)) // JST固定
                    // または ZoneId.systemDefault() を使う場合:
                    // localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime()
                }?.toInstant()
                ?.let { OffsetDateTime.ofInstant(it, ZoneId.systemDefault()) }

        return PhotoMetadata(
            exifIFD0 = exifIFD0,
            exifSubIFD = exifSubIFD,
            gpsDirectory = gpsDirectory,
            capturedTime = captured,
            filePath = localFile,
        )
    }
}
