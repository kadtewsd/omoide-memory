package com.kasakaid.omoidememory.patchtool.filedirmodification.infrastructure

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.patchtool.filedirmodification.domain.ReorganizeMisplacedFilesRepository
import com.kasakaid.omoidememory.patchtool.filedirmodification.domain.ReorganizedFile
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class ReorganizeMisplacedFilesRepositoryImpl(
    private val dslContext: DSLContext,
) : ReorganizeMisplacedFilesRepository {
    override suspend fun update(reorganizedFile: ReorganizedFile) {
        val photoUpdated =
            dslContext
                .update(SYNCED_OMOIDE_PHOTO)
                .set(SYNCED_OMOIDE_PHOTO.SERVER_PATH, reorganizedFile.filePath.toAbsolutePath().toString())
                .set(SYNCED_OMOIDE_PHOTO.CAPTURE_TIME, reorganizedFile.captureTime)
                .where(SYNCED_OMOIDE_PHOTO.FILE_NAME.eq(reorganizedFile.fileName))
                .awaitFirstOrNull<Int>() ?: 0

        if (photoUpdated == 0) {
            val videoUpdated =
                dslContext
                    .update(SYNCED_OMOIDE_VIDEO)
                    .set(SYNCED_OMOIDE_VIDEO.SERVER_PATH, reorganizedFile.filePath.toAbsolutePath().toString())
                    .set(SYNCED_OMOIDE_VIDEO.CAPTURE_TIME, reorganizedFile.captureTime)
                    .where(SYNCED_OMOIDE_VIDEO.FILE_NAME.eq(reorganizedFile.fileName))
                    .awaitFirstOrNull<Int>() ?: 0
        }
    }
}
