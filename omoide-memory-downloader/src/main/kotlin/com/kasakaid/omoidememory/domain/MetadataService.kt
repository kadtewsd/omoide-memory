package com.kasakaid.omoidememory.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.Encoder
import ws.schild.jave.encode.VideoAttributes
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

private val logger = KotlinLogging.logger {}

@Service
class MetadataService {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "heic", "heif", "gif", "webp")
    private val videoExtensions = setOf("mp4", "mov", "avi", "mkv", "3gp", "webm")

    /**
     * ファイルから Metadata を取り出して OmoideMemory として変換します。
     */
    suspend fun extractMetadata(file: File, filePath: Path): Either<MetadataExtractError, OmoideMemory> = withContext(Dispatchers.IO) {
        require(filePath.isRegularFile()) { "ファイルが存在しません: $filePath" }
        when (filePath.extension.lowercase()) {
            in imageExtensions -> extractPhotoMetadata(googleFile = file,filePath)
            in videoExtensions -> extractVideoMetadata(googleFile = file, filePath)
            else -> throw IllegalArgumentException("サポートされていないファイル形式: ${filePath.extension}")
        }
    }



    /**
     * 画像のEXIFメタデータを抽出してOmoideMemory.Photoを生成
     */
    private suspend fun extractPhotoMetadata(
        googleFile: File,
        filePath: Path,
    ): Either<MetadataExtractError, OmoideMemory.Photo> {
        return try {
            Exif.of(filePath).run {
                OmoideMemory.Photo(
                    localPath = filePath,
                    name = googleFile.name,
                    mediaType = googleFile.mimeType,
                    driveFileId = googleFile.id,
                    fileSizeBytes = googleFile.size,
                    locationName = gpsDirectory?.geoLocation?.let { geo ->
                        if (geo.latitude != null && geo.longitude != null) {
                            LocationService.getLocationName(geo.latitude, geo.longitude)
                        } else null
                    },
                    aperture = exifSubIFD?.getDoubleObject(ExifSubIFDDirectory.TAG_FNUMBER)?.toFloat(),
                    shutterSpeed = exifSubIFD?.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME),
                    isoSpeed = exifSubIFD?.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT),
                    focalLength = exifSubIFD?.getDoubleObject(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)?.toFloat(),
                    focalLength35mm = exifSubIFD?.getInteger(ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH),
                    whiteBalance = exifSubIFD?.getString(ExifSubIFDDirectory.TAG_WHITE_BALANCE),
                    imageWidth = exifSubIFD?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)
                        ?: exifIFD0?.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH),
                    imageHeight = exifSubIFD?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)
                        ?: exifIFD0?.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT),
                    orientation = exifIFD0?.getInteger(ExifIFD0Directory.TAG_ORIENTATION),
                    latitude = gpsDirectory?.geoLocation?.latitude,
                    longitude = gpsDirectory?.geoLocation?.longitude,
                    altitude = gpsDirectory?.getDouble(GpsDirectory.TAG_ALTITUDE),
                    captureTime = exifSubIFD?.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)?.let {
                        OffsetDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                    },
                    deviceMake = exifIFD0?.getString(ExifIFD0Directory.TAG_MAKE),
                    deviceModel = exifIFD0?.getString(ExifIFD0Directory.TAG_MODEL),
                ).right()
            }
        } catch (e: Exception) {
            logger.error {  "画像メタデータの抽出に失敗: $filePath"  }
            logger.error { OneLineLogFormatter.format(e) }
            MetadataExtractError(e).left()
        }
    }

    /**
     * 動画のメタデータを抽出してOmoideMemory.Videoを生成
     */
    private suspend fun extractVideoMetadata(
        googleFile: File,
        filePath: Path,
    ): Either<MetadataExtractError, OmoideMemory.Video> {
        return try {
            val thumbnail = generateThumbnail(filePath)

            MultimediaObject(filePath.toFile()).info.let { info ->
                OmoideMemory.Video(
                    localPath = filePath,
                    name = googleFile.name,
                    mediaType = googleFile.mimeType,
                    driveFileId = googleFile.id,
                    fileSizeBytes = googleFile.size,
                    locationName = null, // 動画のGPS情報は標準的には取得困難
                    durationSeconds = info.duration / 1000.0,
                    videoWidth = info.video?.size?.width,
                    videoHeight = info.video?.size?.height,
                    frameRate = info.video?.frameRate?.toDouble(),
                    videoCodec = info.video?.decoder,
                    videoBitrateKbps = info.video?.bitRate?.div(1000),
                    audioCodec = info.audio?.decoder,
                    audioBitrateKbps = info.audio?.bitRate?.div(1000),
                    audioChannels = info.audio?.channels,
                    audioSampleRate = info.audio?.samplingRate,
                    thumbnailImage = thumbnail.getOrNull()?.first,
                    thumbnailMimeType = thumbnail.getOrNull()?.second,
                    captureTime = OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(Files.getLastModifiedTime(filePath).toMillis()),
                        ZoneId.systemDefault()
                    ),
                )
            }.right()
        } catch (e: Exception) {
            logger.error { "動画メタデータの抽出に失敗: $filePath" }
            logger.error { OneLineLogFormatter.format(e) }
            MetadataExtractError(e).left()
        }
    }

    /**
     * 動画の1秒目のフレームをJPEGサムネイルとして生成
     */
    private fun generateThumbnail(videoPath: Path): Either<MetadataExtractError, Pair<ByteArray, String>> {
        return try {
            val tempThumbnail = Files.createTempFile("thumbnail_", ".jpg")

            // ffmpegを直接呼び出し
            val command = listOf(
                "ffmpeg",
                "-ss", "00:00:01",           // 1秒目にシーク
                "-i", videoPath.toString(),  // 入力ファイル
                "-vframes", "1",             // 1フレームだけ抽出
                "-q:v", "2",                 // JPEG品質（2が高品質）
                "-y",                        // 上書き確認なし
                tempThumbnail.toString()
            )

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                val errorOutput = process.inputStream.bufferedReader().readText()
                logger.warn { "ffmpegエラー: $errorOutput" }
                MetadataExtractError(IllegalStateException("サムネイルの生成に失敗しました"))
            }

            val thumbnailBytes = Files.readAllBytes(tempThumbnail)
            Files.delete(tempThumbnail)

            (thumbnailBytes to "image/jpeg").right()
        } catch (e: Exception) {
            logger.warn(e) { "サムネイル生成に失敗: $videoPath" }
            MetadataExtractError(e).left()
        }
    }
}
