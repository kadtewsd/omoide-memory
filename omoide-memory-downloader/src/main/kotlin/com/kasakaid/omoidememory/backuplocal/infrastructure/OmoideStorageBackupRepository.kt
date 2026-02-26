package com.kasakaid.omoidememory.backuplocal.infrastructure

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.OMOIDE_STORAGE_BACKUP
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_PHOTO
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.SYNCED_OMOIDE_VIDEO
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.UUID

data class BackupTarget(
    val id: UUID,
    val fileName: String,
    val serverPath: String,
    val captureTime: OffsetDateTime?,
)

class BackUpKey(
    val id: UUID,
    val path: Path,
)

@Repository
class OmoideStorageBackupRepository(
    private val dslContext: DSLContext,
) {
    suspend fun findBackupTargets(): List<BackupTarget> {
        val photos =
            dslContext
                .selectFrom(SYNCED_OMOIDE_PHOTO)
                .asFlow()
                .map {
                    BackupTarget(
                        id = it.id,
                        fileName = it.fileName,
                        serverPath = it.serverPath,
                        captureTime = it.captureTime,
                    )
                }.toList()

        val videos =
            dslContext
                .selectFrom(SYNCED_OMOIDE_VIDEO)
                .asFlow()
                .map {
                    BackupTarget(
                        id = it.id,
                        fileName = it.fileName,
                        serverPath = it.serverPath,
                        captureTime = it.captureTime,
                    )
                }.toList()

        return photos + videos
    }

    suspend fun exists(key: BackUpKey): Boolean {
        val count =
            dslContext
                .selectCount()
                .from(OMOIDE_STORAGE_BACKUP)
                .where(OMOIDE_STORAGE_BACKUP.SOURCE_ID.eq(key.id))
                .and(OMOIDE_STORAGE_BACKUP.BACKUP_PATH.eq(key.path.toString()))
                .awaitFirstOrNull()
                ?.component1() ?: 0
        return count > 0
    }

    suspend fun saveBackupRecord(
        sourceId: UUID,
        fileName: String,
        backupPath: String,
    ) {
        dslContext
            .insertInto(OMOIDE_STORAGE_BACKUP)
            .set(OMOIDE_STORAGE_BACKUP.ID, MyUUIDGenerator.generateUUIDv7())
            .set(OMOIDE_STORAGE_BACKUP.SOURCE_ID, sourceId)
            .set(OMOIDE_STORAGE_BACKUP.FILE_NAME, fileName)
            .set(OMOIDE_STORAGE_BACKUP.BACKUP_PATH, backupPath)
            .set(OMOIDE_STORAGE_BACKUP.BACKED_UP_AT, OffsetDateTime.now())
            .awaitSingle()
    }
}
