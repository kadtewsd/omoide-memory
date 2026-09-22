package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.domain.VideoMetadataDto
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.SyncedOmoidePhotoRecord
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.records.SyncedOmoideVideoRecord
import java.nio.file.Path
import java.time.OffsetDateTime

object OmoideMemoryTranslator {
    fun translateVideo(video: SyncedOmoideVideoRecord): OmoideMemory.Video =
        OmoideMemory.Video(
            localPath = Path.of(video.serverPath),
            name = video.fileName,
            familyId = video.familyId,
            // mediaType は DB に永続化されていないため、動画は mp4 を一旦セット
            mediaType = "video/mp4",
            driveFileId = video.driveFileId,
            fileSize = video.fileSize?.toLong() ?: 0L,
            captureTime = video.captureTime ?: OffsetDateTime.now(),
            metadata =
                VideoMetadataDto(
                    durationSeconds = video.durationSeconds?.toDoubleOrNull(),
                    videoWidth = video.videoWidth,
                    videoHeight = video.videoHeight,
                    frameRate = video.frameRate?.toDoubleOrNull(),
                    videoCodec = video.videoCodec,
                    videoBitrateKbps = video.videoBitrateKbps?.toLongOrNull(),
                    audioCodec = video.audioCodec,
                    audioBitrateKbps = video.audioBitrateKbps?.toLongOrNull(),
                    audioChannels = video.audioChannels?.toInt(),
                    audioSampleRate = video.audioSampleRate,
                    thumbnailBytes = video.thumbnailImage,
                    thumbnailMimeType = video.thumbnailMimeType,
                ),
        )

    fun translatePhoto(photo: SyncedOmoidePhotoRecord): OmoideMemory.Photo =
        OmoideMemory.Photo(
            localPath = Path.of(photo.serverPath),
            name = photo.fileName,
            familyId = photo.familyId,
            // mediaType は DB に永続化されていないため、写真は jpeg を一旦セット
            mediaType = "image/jpeg",
            driveFileId = photo.driveFileId,
            fileSize = photo.fileSize ?: 0L,
            captureTime = photo.captureTime ?: OffsetDateTime.now(),
            locationName = photo.locationName,
            aperture = photo.aperture?.toFloat(),
            shutterSpeed = photo.shutterSpeed,
            isoSpeed = photo.isoSpeed,
            focalLength = photo.focalLength?.toFloat(),
            focalLength35mm = photo.focalLength_35mm,
            whiteBalance = photo.whiteBalance,
            imageWidth = photo.imageWidth,
            imageHeight = photo.imageHeight,
            orientation = photo.orientation?.toInt(),
            latitude = photo.latitude?.toDouble(),
            longitude = photo.longitude?.toDouble(),
            altitude = photo.altitude?.toDouble(),
            deviceMake = photo.deviceMake,
            deviceModel = photo.deviceModel,
        )
}
