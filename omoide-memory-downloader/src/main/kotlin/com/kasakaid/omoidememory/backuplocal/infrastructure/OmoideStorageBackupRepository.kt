package com.kasakaid.omoidememory.backuplocal.infrastructure

import com.kasakaid.omoidememory.backuplocal.domain.model.BackupStrategy
import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.OMOIDE_STORAGE_BACKUP
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.DSLContext
import org.jooq.Path
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import kotlin.io.path.name

@Repository
class OmoideStorageBackupRepository(
    private val dslContext: DSLContext,
) {
    suspend fun exists(backupPath: java.nio.file.Path): Boolean {
        val count =
            dslContext
                .selectCount()
                .from(OMOIDE_STORAGE_BACKUP)
                .where(OMOIDE_STORAGE_BACKUP.BACKUP_PATH.eq(backupPath.toString()))
                .awaitFirstOrNull()
                ?.component1() ?: 0
        return count > 0
    }

    suspend fun saveBackupRecord(backupStrategy: BackupStrategy) =
        backupStrategy.run {
            dslContext
                .insertInto(OMOIDE_STORAGE_BACKUP)
                .set(OMOIDE_STORAGE_BACKUP.ID, MyUUIDGenerator.generateUUIDv7())
                .set(OMOIDE_STORAGE_BACKUP.FILE_NAME, sourceAbsolutePath.toString())
                .set(OMOIDE_STORAGE_BACKUP.BACKUP_PATH, externalStorageAbsolutePath.toString())
                .set(OMOIDE_STORAGE_BACKUP.BACKED_UP_AT, OffsetDateTime.now())
                .awaitSingle()
        }
}
