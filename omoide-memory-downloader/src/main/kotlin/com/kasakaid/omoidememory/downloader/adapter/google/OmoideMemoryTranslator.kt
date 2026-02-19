package com.kasakaid.omoidememory.downloader.adapter.google

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.domain.LocationService
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
class OmoideMemoryTranslator(
    private val environment: Environment,
    private val metadataService: MetadataService,
    private val locationService: LocationService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Google DriveのFileオブジェクトとダウンロード済みのローカルパスから
     * メタデータを抽出してOmoideMemoryを生成する
     */
    suspend fun translate(googleFile: File, downloadedPath: Path): Option<OmoideMemory> {
        logger.info { "メタデータ抽出開始: ${googleFile.name}" }

        // バイナリからメタデータを抽出
        val metadata = metadataService.extractMetadata(downloadedPath)

        // 位置情報から地名を取得
        val locationName = if (metadata.latitude != null && metadata.longitude != null) {
            locationService.getLocationName(metadata.latitude, metadata.longitude)
        } else {
            null
        }

        return when {
            googleFile.mimeType.startsWith("video/") ||
                    googleFile.name.endsWith(".mp4", ignoreCase = true) ||
                    googleFile.name.endsWith(".mov", ignoreCase = true) -> {
                OmoideMemory.Video(
                    localPath = downloadedPath,
                    name = googleFile.name,
                    mediaType = googleFile.mimeType,
                    driveFileId = googleFile.id,
                    fileSizeBytes = googleFile.size ?: metadata.fileSizeBytes,
                    locationName = locationName,
                    durationSeconds = metadata.durationSeconds,
                    videoWidth = metadata.videoWidth,
                    videoHeight = metadata.videoHeight,
                    frameRate = metadata.frameRate,
                    videoCodec = metadata.videoCodec,
                    videoBitrateKbps = metadata.videoBitrateKbps,
                    audioCodec = metadata.audioCodec,
                    audioBitrateKbps = metadata.audioBitrateKbps,
                    audioChannels = metadata.audioChannels,
                    audioSampleRate = metadata.audioSampleRate,
                    thumbnailImage = metadata.thumbnailImage,
                    thumbnailMimeType = metadata.thumbnailMimeType,
                    captureTime = metadata.captureTime,
                    deviceMake = metadata.deviceMake,
                    deviceModel = metadata.deviceModel,
                    latitude = metadata.latitude,
                    longitude = metadata.longitude,
                    altitude = metadata.altitude,
                ).some()
            }
            googleFile.mimeType.startsWith("image/") ||
                    googleFile.name.endsWith(".jpg", ignoreCase = true) ||
                    googleFile.name.endsWith(".jpeg", ignoreCase = true) ||
                    googleFile.name.endsWith(".png", ignoreCase = true) ||
                    googleFile.name.endsWith(".heic", ignoreCase = true) -> {
                OmoideMemory.Photo(
                    localPath = downloadedPath,
                    name = googleFile.name,
                    mediaType = googleFile.mimeType,
                    driveFileId = googleFile.id,
                    fileSizeBytes = googleFile.size ?: metadata.fileSizeBytes,
                    locationName = locationName,
                    aperture = metadata.aperture,
                    shutterSpeed = metadata.shutterSpeed,
                    isoSpeed = metadata.isoSpeed,
                    focalLength = metadata.focalLength,
                    focalLength35mm = metadata.focalLength35mm,
                    whiteBalance = metadata.whiteBalance,
                    imageWidth = metadata.imageWidth,
                    imageHeight = metadata.imageHeight,
                    orientation = metadata.orientation,
                    latitude = metadata.latitude,
                    longitude = metadata.longitude,
                    altitude = metadata.altitude,
                    captureTime = metadata.captureTime,
                    deviceMake = metadata.deviceMake,
                    deviceModel = metadata.deviceModel,
                ).some()
            }
            else -> {
                logger.warn { "サポートされていないファイル形式: ${googleFile.name} (${googleFile.mimeType})" }
                None
            }
        }
    }
}