package com.kasakaid.omoidememory.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.domain.LocationService
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import ws.schild.jave.MultimediaObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

private val logger = KotlinLogging.logger {}

class MetadataExtractError(val ex: Exception)

@Service
class OmoideMemoryMetadataService(
    private val locationService: LocationService,
) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "heic", "heif", "gif", "webp")
    private val videoExtensions = setOf("mp4", "mov", "avi", "mkv", "3gp", "webm")

    /**
     * Google DriveのFileとダウンロード済みのローカルパスから
     * メタデータを抽出してOmoideMemoryを生成
     */
    suspend fun extractOmoideMemory(
        googleFile: File,
        downloadedPath: Path,
    ): Either<MetadataExtractError, OmoideMemory> = withContext(Dispatchers.IO) {
        require(downloadedPath.isRegularFile()) { "ファイルが存在しません: $downloadedPath" }

        val extension = downloadedPath.extension.lowercase()

        when {
            extension in imageExtensions -> extractPhotoMetadata(googleFile, downloadedPath)
            extension in videoExtensions -> extractVideoMetadata(googleFile, downloadedPath)
            else -> throw IllegalArgumentException("サポートされていないファイル形式: $extension")
        }
    }

    /**
     * 画像のEXIFメタデータを抽出してOmoideMemory.Photoを生成
     */
    private suspend fun extractPhotoMetadata(
        googleFile: File,
        filePath: Path,
    ): OmoideMemory.Photo {
        return try {
            MetadataService.Exif.of(filePath).run {
                OmoideMemory.Photo(
                    localPath = filePath,
                    name = googleFile.name,
                    mediaType = googleFile.mimeType,
                    driveFileId = googleFile.id,
                    fileSizeBytes = googleFile.size?.toInt() ?: Files.size(filePath).toInt(),
                    locationName = gpsDirectory?.geoLocation?.let { geo ->
                        if (geo.latitude != null && geo.longitude != null) {
                            locationService.getLocationName(geo.latitude, geo.longitude)
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
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "画像メタデータの抽出に失敗: $filePath" }
            // EXIFが存在しない場合はデフォルト値
            OmoideMemory.Photo(
                localPath = filePath,
                name = googleFile.name,
                mediaType = googleFile.mimeType,
                driveFileId = googleFile.id,
                fileSizeBytes = googleFile.size?.toInt() ?: Files.size(filePath).toInt(),
                locationName = null,
                aperture = null,
                shutterSpeed = null,
                isoSpeed = null,
                focalLength = null,
                focalLength35mm = null,
                whiteBalance = null,
                imageWidth = null,
                imageHeight = null,
                orientation = null,
                latitude = null,
                longitude = null,
                altitude = null,
                captureTime = null,
                deviceMake = null,
                deviceModel = null,
            )
        }
    }

    /**
     * 動画のメタデータを抽出してOmoideMemory.Videoを生成
     */
    private suspend fun extractVideoMetadata(
        googleFile: File,
        filePath: Path,
    ): OmoideMemory.Video {
        return try {
            val multimediaObject = MultimediaObject(filePath.toFile())
            val info = multimediaObject.info

            // 動画情報
            val durationSeconds = info.duration / 1000.0
            val videoInfo = info.video
            val audioInfo = info.audio

            // 動画コーデック・解像度・フレームレート
            val videoWidth = videoInfo?.size?.width
            val videoHeight = videoInfo?.size?.height
            val frameRate = videoInfo?.frameRate?.toDouble()
            val videoCodec = videoInfo?.decoder
            val videoBitrateKbps = videoInfo?.bitRate?.div(1000)

            // 音声情報
            val audioCodec = audioInfo?.decoder
            val audioBitrateKbps = audioInfo?.bitRate?.div(1000)
            val audioChannels = audioInfo?.channels
            val audioSampleRate = audioInfo?.samplingRate

            // サムネイル生成（1秒目のフレーム）
            val (thumbnailImage, thumbnailMimeType) = generateThumbnail(filePath)

            // 撮影日時（動画ファイルの作成日時をフォールバック）
            val captureTime = OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(Files.getLastModifiedTime(filePath).toMillis()),
                ZoneId.systemDefault()
            )

            val fileSize = Files.size(filePath).toInt()

            OmoideMemory.Video(
                localPath = filePath,
                name = googleFile.name,
                mediaType = googleFile.mimeType,
                driveFileId = googleFile.id,
                fileSizeBytes = googleFile.size?.toInt() ?: fileSize,
                locationName = null, // 動画のGPS情報は標準的には取得困難
                durationSeconds = durationSeconds,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                frameRate = frameRate,
                videoCodec = videoCodec,
                videoBitrateKbps = videoBitrateKbps,
                audioCodec = audioCodec,
                audioBitrateKbps = audioBitrateKbps,
                audioChannels = audioChannels,
                audioSampleRate = audioSampleRate,
                thumbnailImage = thumbnailImage,
                thumbnailMimeType = thumbnailMimeType,
                captureTime = captureTime,
                deviceMake = null,
                deviceModel = null,
                latitude = null,
                longitude = null,
            )
        } catch (e: Exception) {
            logger.error(e) { "動画メタデータの抽出に失敗: $filePath" }
            val fileSize = Files.size(filePath).toInt()
            OmoideMemory.Video(
                localPath = filePath,
                name = googleFile.name,
                mediaType = googleFile.mimeType,
                driveFileId = googleFile.id,
                fileSizeBytes = googleFile.size?.toInt() ?: fileSize,
                locationName = null,
                durationSeconds = null,
                videoWidth = null,
                videoHeight = null,
                frameRate = null,
                videoCodec = null,
                videoBitrateKbps = null,
                audioCodec = null,
                audioBitrateKbps = null,
                audioChannels = null,
                audioSampleRate = null,
                thumbnailImage = null,
                thumbnailMimeType = null,
                captureTime = null,
                deviceMake = null,
                deviceModel = null,
                latitude = null,
                longitude = null,
            )
        }
    }

    /**
     * 動画の1秒目のフレームをJPEGサムネイルとして生成
     */
    private fun generateThumbnail(videoPath: Path): Pair<ByteArray?, String?> {
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
                return null to null
            }

            val thumbnailBytes = Files.readAllBytes(tempThumbnail)
            Files.delete(tempThumbnail)

            thumbnailBytes to "image/jpeg"
        } catch (e: Exception) {
            logger.warn(e) { "サムネイル生成に失敗: $videoPath" }
            null to null
        }
    }
}