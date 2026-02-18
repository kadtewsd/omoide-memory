package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Repository
class SyncedMemoryRepository(
    private val dslContext: DSLContext,
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
        return memory
    }

    private suspend fun savePhoto(memory: OmoideMemory.Photo) {
        val record = dslContext.insertInto(SYNCED_OMOIDE_PHOTO)
            .set(SYNCED_OMOIDE_PHOTO.FILE_NAME, memory.name)
            .set(SYNCED_OMOIDE_PHOTO.SERVER_PATH, memory.localPath.toString())
            .set(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME, memory.captureTime ?: OffsetDateTime.now())
            .set(SYNCED_OMOIDE_PHOTO.LATITUDE, memory.latitude?.toBigDecimal())
            .set(SYNCED_OMOIDE_PHOTO.LONGITUDE, memory.longitude?.toBigDecimal())
            .set(SYNCED_OMOIDE_PHOTO.LOCATION_NAME, memory.locationName)
            .set(SYNCED_OMOIDE_PHOTO.IMAGE_WIDTH, memory.imageWidth)
            .set(SYNCED_OMOIDE_PHOTO.IMAGE_HEIGHT, memory.imageHeight)
            .set(SYNCED_OMOIDE_PHOTO.FILE_SIZE, memory.fileSizeBytes)
            .set(SYNCED_OMOIDE_PHOTO.CREATED_BY, "downloader")
            .set(SYNCED_OMOIDE_PHOTO.CREATED_AT, OffsetDateTime.now())
            .returning()
            
        Mono.from(record).awaitSingle()
    }

    private suspend fun saveVideo(memory: OmoideMemory.Video) {
        val record = dslContext.insertInto(SYNCED_OMOIDE_VIDEO)
            .set(SYNCED_OMOIDE_VIDEO.FILE_NAME, memory.name)
            .set(SYNCED_OMOIDE_VIDEO.SERVER_PATH, memory.localPath.toString())
            .set(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME, memory.captureTime ?: OffsetDateTime.now())
            .set(SYNCED_OMOIDE_VIDEO.LATITUDE, memory.latitude?.toBigDecimal())
            .set(SYNCED_OMOIDE_VIDEO.LONGITUDE, memory.longitude?.toBigDecimal())
            .set(SYNCED_OMOIDE_VIDEO.LOCATION_NAME, memory.locationName)
            .set(
                SYNCED_OMOIDE_VIDEO.DURATION_SECONDS,
                memory.durationSeconds?.toBigDecimal()
            )
            .set(SYNCED_OMOIDE_VIDEO.VIDEO_WIDTH, memory.videoWidth)
            .set(SYNCED_OMOIDE_VIDEO.VIDEO_HEIGHT, memory.videoHeight)
            .set(SYNCED_OMOIDE_VIDEO.FILE_SIZE, memory.fileSizeBytes)
            .set(SYNCED_OMOIDE_VIDEO.CREATED_BY, "downloader")
            .set(SYNCED_OMOIDE_VIDEO.CREATED_AT, OffsetDateTime.now())
            .returning()

        Mono.from(record).awaitSingle()
    }

    override suspend fun existsPhotoByFileName(fileName: String): Boolean {
        // Check both tables
        val photoExists = Mono.from(
            dslContext.selectCount()
                .from(SYNCED_OMOIDE_PHOTO)
                .where(SYNCED_OMOIDE_PHOTO.FILE_NAME.eq(fileName))
        ).awaitSingle().component1() ?: 0

        return (photoExists > 0)
    }

    override suspend fun existsVideoByFileName(fileName: String): Boolean {
        val videoExists = Mono.from(
            dslContext.selectCount()
                .from(SYNCED_OMOIDE_VIDEO)
                .where(SYNCED_OMOIDE_VIDEO.FILE_NAME.eq(fileName))
        ).awaitSingle().component1() ?: 0

        return videoExists > 0
    }
}