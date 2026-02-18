package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememor.r2dbc.R2DBCDSLContext
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import kotlin.text.set

@Repository
class SyncedMemoryRepository(
    private val dslContext: R2DBCDSLContext,
): OmoideMemoryRepository {

    override suspend fun save(memory: OmoideMemory): OmoideMemory {
        when (memory) {
            is OmoideMemory.Video -> {
                saveVideo(memory)
            }
            is OmoideMemory.Photo -> {
                savePhoto(memory)
            }
        }
    }

    private suspend fun savePhoto(omoideMemory: OmoideMemory.Photo) {
        dslContext.get().insertInto(SYNCED_OMOIDE_PHOTO)
            .set(SYNCED_OMOIDE_PHOTO.FILE_NAME, fileName)
            .set(SYNCED_OMOIDE_PHOTO.SERVER_PATH, serverPath)
            .set(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME, metadata.captureTime ?: OffsetDateTime.now())
            .set(SYNCED_OMOIDE_PHOTO.LATITUDE, metadata.latitude?.toBigDecimal())
            .set(SYNCED_OMOIDE_PHOTO.LONGITUDE, metadata.longitude?.toBigDecimal())
            .set(SYNCED_OMOIDE_PHOTO.LOCATION_NAME, locationName)
            .set(SYNCED_OMOIDE_PHOTO.IMAGE_WIDTH, metadata.imageWidth)
            .set(SYNCED_OMOIDE_PHOTO.IMAGE_HEIGHT, metadata.imageHeight)
            .set(SYNCED_OMOIDE_PHOTO.FILE_SIZE_BYTES, metadata.fileSizeBytes)
            .set(SYNCED_OMOIDE_PHOTO.CREATED_BY, "downloader")
            .set(SYNCED_OMOIDE_PHOTO.CREATED_AT, OffsetDateTime.now())
            .returning()
            .awaitSingle()
    }

    private suspend fun saveVideo(omoideMemory: OmoideMemory.Video) {
        val record = dslContext.get().insertInto(SYNCED_OMOIDE_VIDEO)
            .set(SYNCED_OMOIDE_VIDEO.FILE_NAME, fileName)
            .set(SYNCED_OMOIDE_VIDEO.SERVER_PATH, serverPath)
            .set(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME, metadata.captureTime ?: OffsetDateTime.now())
            .set(SYNCED_OMOIDE_VIDEO.LATITUDE, metadata.latitude?.toBigDecimal())
            .set(SYNCED_OMOIDE_VIDEO.LONGITUDE, metadata.longitude?.toBigDecimal())
            .set(SYNCED_OMOIDE_VIDEO.LOCATION_NAME, locationName)
            .set(
                SYNCED_OMOIDE_VIDEO.DURATION_SECONDS,
                metadata.durationSeconds?.toBigDecimal()
            ) // Assuming int in schema for now
            .set(SYNCED_OMOIDE_VIDEO.VIDEO_WIDTH, metadata.videoWidth)
            .set(SYNCED_OMOIDE_VIDEO.VIDEO_HEIGHT, metadata.videoHeight)
            .set(SYNCED_OMOIDE_VIDEO.FILE_SIZE_BYTES, metadata.fileSizeBytes)
            .set(SYNCED_OMOIDE_VIDEO.CREATED_BY, "downloader")
            .set(SYNCED_OMOIDE_VIDEO.CREATED_AT, OffsetDateTime.now())
            .returning()

        Mono.from(record).awaitSingle()
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
        val videoExists = dslContext.get().selectCount()
            .from(SYNCED_OMOIDE_VIDEO)
            .where(SYNCED_OMOIDE_VIDEO.FILE_NAME.eq(fileName))
            .awaitSingle().component1() ?: 0

        return videoExists > 0
    }
}