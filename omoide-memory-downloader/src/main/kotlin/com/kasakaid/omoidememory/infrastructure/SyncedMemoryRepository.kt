package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.commentimport.domain.model.FileName
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.Condition
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class SyncedMemoryRepository(
    private val dslContext: DSLGenerator,
) : OmoideMemoryRepository {
    /**
     * 呼び出し元では、 TransactionalOperator.executeAndAwait から実行されることにより、本クラスの awaitSingle などを実行すると、
     * 内部で Publisher 化されるので、トランザクションは伝わる
     */
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
            val insertOnlyFields =
                mapOf(
                    ID to MyUUIDGenerator.generateUUIDv7(),
                    FILE_NAME to memory.name,
                    CREATED_BY to "downloader",
                    CREATED_AT to OffsetDateTime.now(),
                )
            val updatableFields =
                mapOf(
                    FAMILY_ID to memory.familyId,
                    SERVER_PATH to memory.localPath.toString(),
                    CAPTURE_TIME to memory.captureTime,
                    LATITUDE to memory.latitude?.toBigDecimal(),
                    LONGITUDE to memory.longitude?.toBigDecimal(),
                    ALTITUDE to memory.altitude?.toBigDecimal(),
                    LOCATION_NAME to memory.locationName,
                    DEVICE_MAKE to memory.deviceMake,
                    DEVICE_MODEL to memory.deviceModel,
                    APERTURE to memory.aperture?.toBigDecimal(),
                    SHUTTER_SPEED to memory.shutterSpeed,
                    ISO_SPEED to memory.isoSpeed,
                    FOCAL_LENGTH to memory.focalLength?.toBigDecimal(),
                    FOCAL_LENGTH_35MM to memory.focalLength35mm,
                    WHITE_BALANCE to memory.whiteBalance,
                    IMAGE_WIDTH to memory.imageWidth,
                    IMAGE_HEIGHT to memory.imageHeight,
                    ORIENTATION to memory.orientation?.toShort(),
                    FILE_SIZE to memory.fileSize,
                    DRIVE_FILE_ID to memory.driveFileId,
                )
            dslContext
                .invoke()
                .insertInto(this)
                .set(insertOnlyFields + updatableFields)
                .onConflict(FILE_NAME)
                .doUpdate()
                .set(updatableFields)
                .returning()
                .awaitFirstOrNull()
        }
    }

    private suspend fun saveVideo(memory: OmoideMemory.Video) {
        SYNCED_OMOIDE_VIDEO.run {
            val insertOnlyFields =
                mapOf(
                    ID to MyUUIDGenerator.generateUUIDv7(),
                    FILE_NAME to memory.name,
                    CREATED_BY to "downloader",
                    CREATED_AT to OffsetDateTime.now(),
                )

            val baseUpdatableFields =
                memory.run {
                    mapOf(
                        FAMILY_ID to familyId,
                        SERVER_PATH to localPath.toString(),
                        CAPTURE_TIME to captureTime,
                        FILE_SIZE to fileSize,
                        DRIVE_FILE_ID to driveFileId,
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
            val updatableFields = baseUpdatableFields + metadataFields
            dslContext
                .invoke()
                .insertInto(this)
                .set(insertOnlyFields + updatableFields)
                .onConflict(FILE_NAME)
                .doUpdate()
                .set(updatableFields)
                .returning()
                .awaitFirstOrNull()
        }
    }

    override suspend fun existsPhotoByFileName(fileName: String): Boolean {
        // Check both tables
        val photoExists =
            dslContext
                .invoke()
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
                .invoke()
                .selectCount()
                .from(SYNCED_OMOIDE_VIDEO)
                .where(SYNCED_OMOIDE_VIDEO.FILE_NAME.eq(fileName))
                .awaitSingle()
                .component1() ?: 0

        return videoExists > 0
    }

    private suspend fun fetchVideoBy(condition: Condition): OmoideMemory? =
        dslContext
            .invoke()
            .selectFrom(SYNCED_OMOIDE_VIDEO)
            .where(condition)
            .asFlow()
            .map {
                OmoideMemoryTranslator.translateVideo(it)
            }.firstOrNull()

    override suspend fun findByFileName(fileName: FileName): OmoideMemory? =
        fetchPhotoBy(
            SYNCED_OMOIDE_PHOTO.FILE_NAME.eq(fileName),
        ) ?: fetchVideoBy(
            SYNCED_OMOIDE_VIDEO.FILE_NAME.eq(fileName),
        )

    override suspend fun findByLikeFileName(fileName: FileName): OmoideMemory? =
        fetchPhotoBy(
            SYNCED_OMOIDE_PHOTO.FILE_NAME.like("%$fileName%", '\\'),
        ) ?: fetchVideoBy(
            SYNCED_OMOIDE_VIDEO.FILE_NAME.like("%$fileName%", '\\'),
        )

    override suspend fun deleteByFileName(fileName: FileName) {
        dslContext
            .invoke()
            .deleteFrom(SYNCED_OMOIDE_PHOTO)
            .where(SYNCED_OMOIDE_PHOTO.FILE_NAME.eq(fileName))
            .asFlow()
            .firstOrNull()

        dslContext
            .invoke()
            .deleteFrom(SYNCED_OMOIDE_VIDEO)
            .where(SYNCED_OMOIDE_VIDEO.FILE_NAME.eq(fileName))
            .asFlow()
            .firstOrNull()
    }

    private suspend fun fetchPhotoBy(condition: Condition): OmoideMemory? =
        dslContext
            .invoke()
            .selectFrom(SYNCED_OMOIDE_PHOTO)
            .where(condition)
            .asFlow()
            .map {
                OmoideMemoryTranslator.translatePhoto(it)
            }.firstOrNull()
}
