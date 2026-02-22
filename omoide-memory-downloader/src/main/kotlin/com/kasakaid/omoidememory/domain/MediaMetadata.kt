package com.kasakaid.omoidememory.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.drew.lang.GeoLocation
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.google.api.services.drive.model.File
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneId

sealed interface MediaMetadata {
    val capturedTime: OffsetDateTime?
    val filePath: Path

    suspend fun toMedia(sourceFile: SourceFile): Either<MetadataExtractError, OmoideMemory>
}

/**
 * メタデータ抽出元のファイル情報
 * Google Driveまたはローカルファイルのどちらからでも作成可能
 */
class SourceFile(
    val name: String,
    val mimeType: String,
    val size: Long,
    val driveFileId: String?, // Google Drive由来の場合のみ
) {
    companion object {
        /**
         * Google Drive APIのFileから作成
         */
        fun fromGoogleDrive(googleFile: File): SourceFile =
            SourceFile(
                name = googleFile.name,
                mimeType = googleFile.mimeType,
                size = googleFile.size.toLong(),
                driveFileId = googleFile.id,
            )

        /**
         * ローカルファイルから作成
         */
        fun fromLocalFile(localPath: Path): SourceFile =
            SourceFile(
                name = localPath.fileName.toString(),
                mimeType = Extension.of(localPath).mimeType,
                size =
                    java.nio.file.Files
                        .size(localPath),
                driveFileId = null, // ローカルファイルはnull
            )
    }
}

class VideoMetadata(
    override val capturedTime: OffsetDateTime,
    override val filePath: Path,
) : MediaMetadata {
    override suspend fun toMedia(sourceFile: SourceFile): Either<MetadataExtractError, OmoideMemory> =
        either {
            OmoideMemory.Video(
                localPath = filePath,
                name = sourceFile.name,
                mediaType = sourceFile.mimeType,
                driveFileId = sourceFile.driveFileId,
                fileSize = sourceFile.size,
                captureTime = capturedTime,
                metadata =
                    VideoMetaInfoExtractor
                        .extractAll(filePath)
                        .bind(),
            )
        }
}

val logger = KotlinLogging.logger {}

class PhotoMetadata(
    val exifIFD0: ExifIFD0Directory?,
    val exifSubIFD: ExifSubIFDDirectory?,
    val gpsDirectory: GpsDirectory?,
    override val capturedTime: OffsetDateTime?,
    override val filePath: Path,
) : MediaMetadata {
    override suspend fun toMedia(sourceFile: SourceFile): Either<MetadataExtractError, OmoideMemory> =
        try {
            OmoideMemory
                .Photo(
                    localPath = filePath,
                    name = sourceFile.name,
                    mediaType = sourceFile.mimeType,
                    driveFileId = sourceFile.driveFileId,
                    fileSize = sourceFile.size,
                    locationName =
                        gpsDirectory?.geoLocation?.let { geo ->
                            if (geo.isZero) {
                                LocationService.getLocationName(geo.latitude, geo.longitude)
                            } else {
                                null
                            }
                        },
                    aperture = exifSubIFD?.getDoubleObject(ExifSubIFDDirectory.TAG_FNUMBER)?.toFloat(),
                    shutterSpeed = exifSubIFD?.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME),
                    isoSpeed = exifSubIFD?.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT),
                    focalLength = exifSubIFD?.getDoubleObject(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)?.toFloat(),
                    focalLength35mm = exifSubIFD?.getInteger(ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH),
                    whiteBalance = exifSubIFD?.getString(ExifSubIFDDirectory.TAG_WHITE_BALANCE),
                    imageWidth =
                        exifSubIFD?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)
                            ?: exifIFD0?.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH),
                    imageHeight =
                        exifSubIFD?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)
                            ?: exifIFD0?.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT),
                    orientation = exifIFD0?.getInteger(ExifIFD0Directory.TAG_ORIENTATION),
                    latitude = gpsDirectory?.geoLocation?.latitude,
                    longitude = gpsDirectory?.geoLocation?.longitude,
                    altitude = gpsDirectory?.getDouble(GpsDirectory.TAG_ALTITUDE),
                    captureTime =
                        exifSubIFD?.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)?.let {
                            OffsetDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                        },
                    deviceMake = exifIFD0?.getString(ExifIFD0Directory.TAG_MAKE),
                    deviceModel = exifIFD0?.getString(ExifIFD0Directory.TAG_MODEL),
                ).right()
        } catch (e: Exception) {
            logger.error(e) { "画像メタデータの抽出に失敗: ${this.filePath}" }
            MetadataExtractError(e).left()
        }
}
