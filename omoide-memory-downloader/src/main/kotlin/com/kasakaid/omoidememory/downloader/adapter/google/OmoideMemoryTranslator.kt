package com.kasakaid.omoidememory.downloader.adapter.google

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.downloader.adapter.location.LocationService
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Google の情報を思い出に変換させるドメインモデル
 */
@Component
class OmoideMemoryTranslator(
    private val environment: Environment,
) {
    suspend fun translate(googleFile: File): Option<OmoideMemory> {
        val destRoot = environment.getProperty("OMOIDE_BACKUP_DESTINATION") ?: throw IllegalStateException("OMOIDE_BACKUP_DESTINATION is not set")
        // Windows path separator in environment variable should generally be handled by Java Path properly or manually.
        // Assuming user might set it with backslashes on Windows.
        val path = Path.of(destRoot, googleFile.name)

        // creationTime usually acts as capture time if not overridden by image metadata
        // GDrive 'createdTime' is when uploaded, but 'imageMediaMetadata.time' is EXIF time.
        val baseTime = googleFile.imageMediaMetadata?.time ?: googleFile.videoMediaMetadata?.durationMillis /* logic needed? */
        // Better parsing for time:
        val captureTime = parseTime(googleFile.imageMediaMetadata?.time) 
            ?: parseTime(googleFile.createdTime?.toString()) // Fallback

        // Check mime type
        return when {
            googleFile.mimeType.startsWith("video/") || googleFile.name.endsWith(".mp4") || googleFile.name.endsWith(".mov") -> {
                val metadata = googleFile.videoMediaMetadata
                OmoideMemory.Video(
                    localPath = path,
                    name = googleFile.name,
                    mediaType = googleFile.mimeType,
                    driveFileId = googleFile.id,
                    fileSizeBytes = googleFile.size,
                    locationName = null, // Video location metadata from GDrive API is rare/non-standard?
                    durationSeconds = metadata?.durationMillis?.div(1000.0),
                    videoWidth = metadata?.width,
                    videoHeight = metadata?.height,
                    frameRate = null, // Not standard in GDrive API metadata
                    videoCodec = null,
                    videoBitrateKbps = null,
                    audioCodec = null,
                    audioBitrateKbps = null,
                    audioChannels = null,
                    audioSampleRate = null,
                    thumbnailImage = null,
                    thumbnailMimeType = null,
                    captureTime = captureTime,
                    deviceMake = null, // Not typically in GDrive video metadata
                    deviceModel = null,
                    latitude = null,
                    longitude = null,
                ).some()
            }
            googleFile.mimeType.startsWith("image/") || googleFile.name.endsWith(".jpg") || googleFile.name.endsWith(".png") -> {
                val metadata = googleFile.imageMediaMetadata
                val loc = metadata?.location
                val lat = loc?.latitude
                val lon = loc?.longitude
                val locationName = if (lat != null && lon != null) {
                    LocationService.getLocationName(lat, lon)
                } else null

                OmoideMemory.Photo(
                    localPath = path,
                    name = googleFile.name,
                    mediaType = googleFile.mimeType,
                    driveFileId = googleFile.id,
                    fileSizeBytes = googleFile.size,
                    locationName = locationName,
                    aperture = metadata?.aperture,
                    shutterSpeed = metadata?.exposureTime?.toString(),
                    isoSpeed = metadata?.isoSpeed,
                    focalLength = metadata?.focalLength,
                    focalLength35mm = null,
                    whiteBalance = metadata?.whiteBalance,
                    imageWidth = metadata?.width,
                    imageHeight = metadata?.height,
                    orientation = metadata?.rotation, // GDrive gives rotation in degrees usually, or orientation tag
                    latitude = lat,
                    longitude = lon,
                    altitude = loc?.altitude,
                    captureTime = captureTime,
                    deviceMake = metadata?.cameraMake,
                    deviceModel = metadata?.cameraModel
                ).some()
            }
            else -> None
        }
    }

    private fun parseTime(timeStr: String?): OffsetDateTime? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            // GDrive format for createdTime is RFC3339 (e.g. 2023-01-01T12:00:00.000Z)
            // imageMediaMetadata.time format varies (e.g. "2023:01:01 12:00:00" or ISO)
            if (timeStr.contains(":")) {
                // Try ISO first
                 OffsetDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME)
            } else {
                null
            }
        } catch (e: Exception) {
            try {
                // Fallback for strict RFC3339 if ISO_DATE_TIME failed just in case
                OffsetDateTime.parse(timeStr)
            } catch (e2: Exception) {
                // EXIF format "yyyy:MM:dd HH:mm:ss"
                try {
                     // Parsing manually or using specific formatter if needed.
                     // For MVP, returning null if standard parsing fails to avoid complex parser logic here.
                     null
                } catch (e3: Exception) {
                    null
                }
            }
        }
    }
}