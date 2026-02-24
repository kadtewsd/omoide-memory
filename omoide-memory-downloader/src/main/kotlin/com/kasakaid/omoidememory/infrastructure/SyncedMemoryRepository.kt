package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.r2dbc.R2DBCDSLContext
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.core.env.Environment
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class SyncedMemoryRepository(
    private val dslContext: R2DBCDSLContext,
    environment: Environment,
) : OmoideMemoryRepository {
    val familyId = environment.getProperty("OMOIDE_FOLDER_ID")!!

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
            dslContext
                .get()
                .insertInto(this)
                .set(ID, MyUUIDGenerator.generateUUIDv7())
                .set(FILE_NAME, memory.name)
                .set(FAMILY_ID, familyId)
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
                .set(FILE_SIZE, memory.fileSize)
                .set(DRIVE_FILE_ID, memory.driveFileId)
                .set(CREATED_BY, "downloader")
                .set(CREATED_AT, OffsetDateTime.now())
                .returning()
                .awaitSingle()
        }
    }

    private suspend fun saveVideo(memory: OmoideMemory.Video) {
        SYNCED_OMOIDE_VIDEO.run {
            val baseFields =
                memory.run {
                    mapOf(
                        ID to MyUUIDGenerator.generateUUIDv7(),
                        FILE_NAME to name,
                        FAMILY_ID to familyId,
                        SERVER_PATH to localPath.toString(),
                        CAPTURE_TIME to captureTime,
                        FILE_SIZE to fileSize,
                        DRIVE_FILE_ID to driveFileId,
                        CREATED_BY to "downloader",
                        CREATED_AT to OffsetDateTime.now(),
                    )
                }

            val metadataFields =
                memory.metadata.run {
                    mapOf(
                        DURATION_SECONDS to durationSeconds?.toBigDecimal(),
                        VIDEO_WIDTH to videoWidth,
                        VIDEO_HEIGHT to videoHeight,
                        FRAME_RATE to frameRate?.toBigDecimal(),
                        VIDEO_CODEC to videoCodec,
                        VIDEO_BITRATE_KBPS to videoBitrateKbps,
                        AUDIO_CODEC to audioCodec,
                        AUDIO_BITRATE_KBPS to audioBitrateKbps,
                        AUDIO_CHANNELS to audioChannels?.toShort(),
                        AUDIO_SAMPLE_RATE to audioSampleRate,
                        THUMBNAIL_IMAGE to thumbnailBytes,
                        THUMBNAIL_MIME_TYPE to thumbnailMimeType,
                    )
                }
            dslContext
                .get()
                .insertInto(this)
                .set(baseFields + metadataFields)
                .returning()
                .awaitSingle()
        }
    }

    override suspend fun existsPhotoByFileName(fileName: String): Boolean {
        // Check both tables
        val photoExists =
            dslContext
                .get()
                .selectCount()
                .from(SYNCED_OMOIDE_PHOTO)
                .where(SYNCED_OMOIDE_PHOTO.FILE_NAME.eq(fileName))
                .awaitSingle()
                .component1() ?: 0

        return (photoExists > 0)
    }

    override suspend fun existsVideoByFileName(fileName: String): Boolean {
        val videoExists =
            dslContext
                .get()
                .selectCount()
                .from(SYNCED_OMOIDE_VIDEO)
                .where(SYNCED_OMOIDE_VIDEO.FILE_NAME.eq(fileName))
                .awaitSingle()
                .component1() ?: 0

        return videoExists > 0
    }
}
