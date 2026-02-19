package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.r2dbc.R2DBCDSLContext
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Repository
class SyncedMemoryRepository(
    private val dslContext: R2DBCDSLContext,
) : OmoideMemoryRepository {

    override suspend fun save(memory: OmoideMemory): OmoideMemory {
        when (memory) {
            is OmoideMemory.Video -> {
                saveVideo(memory)
            }

            is OmoideMemory.Photo -> {
                savePhoto(memory)
            }
        }
        return memory
    }
    private suspend fun savePhoto(memory: OmoideMemory.Photo) {
        SYNCED_OMOIDE_PHOTO.run {
            dslContext.get().insertInto(this)
                .set(FILE_NAME, memory.name)
                .set(SERVER_PATH, memory.localPath.toString())
                .set(CAPTURE_TIME, memory.captureTime)
                .set(LATITUDE, memory.latitude?.toBigDecimal())
                .set(LONGITUDE, memory.longitude?.toBigDecimal())
                .set(ALTITUDE, memory.altitude?.toBigDecimal())
                .set(LOCATION_NAME, memory.locationName)
                .set(DEVICE_MAKE, memory.deviceMake)
                .set(DEVICE_MODEL, memory.deviceModel)
                .set(APERTURE, memory.aperture?.toBigDecimal())
                .set(SHUTTER_SPEED, memory.shutterSpeed)
                .set(ISO_SPEED, memory.isoSpeed)
                .set(FOCAL_LENGTH, memory.focalLength?.toBigDecimal())
                .set(FOCAL_LENGTH_35MM, memory.focalLength35mm)
                .set(WHITE_BALANCE, memory.whiteBalance)
                .set(IMAGE_WIDTH, memory.imageWidth)
                .set(IMAGE_HEIGHT, memory.imageHeight)
                .set(ORIENTATION, memory.orientation?.toShort())
                .set(FILE_SIZE_BYTES, memory.fileSizeBytes.toLong())
                .set(CREATED_BY, "downloader")
                .set(CREATED_AT, OffsetDateTime.now())
                .returning()
                .awaitSingle()
        }
    }

    private suspend fun saveVideo(memory: OmoideMemory.Video) {
        SYNCED_OMOIDE_VIDEO.run {
            dslContext.get().insertInto(this)
                .set(FILE_NAME, memory.name)
                .set(SERVER_PATH, memory.localPath.toString())
                .set(CAPTURE_TIME, memory.captureTime)
                .set(DURATION_SECONDS, memory.durationSeconds?.toBigDecimal())
                .set(VIDEO_WIDTH, memory.videoWidth)
                .set(VIDEO_HEIGHT, memory.videoHeight)
                .set(FRAME_RATE, memory.frameRate?.toBigDecimal())
                .set(VIDEO_CODEC, memory.videoCodec)
                .set(VIDEO_BITRATE_KBPS, memory.videoBitrateKbps)
                .set(AUDIO_CODEC, memory.audioCodec)
                .set(AUDIO_BITRATE_KBPS, memory.audioBitrateKbps)
                .set(AUDIO_CHANNELS, memory.audioChannels?.toShort())
                .set(AUDIO_SAMPLE_RATE, memory.audioSampleRate)
                .set(THUMBNAIL_IMAGE, memory.thumbnailImage)
                .set(THUMBNAIL_MIME_TYPE, memory.thumbnailMimeType)
                .set(FILE_SIZE, memory.fileSizeBytes)
                .set(CREATED_BY, "downloader")
                .set(CREATED_AT, OffsetDateTime.now())
                .returning()
                .awaitSingle()
        }
    }
    override suspend fun existsPhotoByFileName(fileName: String): Boolean {
        // Check both tables
        val photoExists =
            dslContext.get().selectCount()
                .from(SYNCED_OMOIDE_PHOTO)
                .where(SYNCED_OMOIDE_PHOTO.FILE_NAME.eq(fileName))
                .awaitSingle().component1() ?: 0

        return (photoExists > 0)
    }

    override suspend fun existsVideoByFileName(fileName: String): Boolean {
        val videoExists =
            dslContext.get().selectCount()
                .from(SYNCED_OMOIDE_VIDEO)
                .where(SYNCED_OMOIDE_VIDEO.FILE_NAME.eq(fileName))
                .awaitSingle().component1() ?: 0

        return videoExists > 0
    }
}