package com.kasakaid.omoidememory.backuplocal.service

import com.kasakaid.omoidememory.backuplocal.domain.model.BackupStrategy
import com.kasakaid.omoidememory.backuplocal.infrastructure.OmoideStorageBackupRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.name

@Service
class BackupLocalStorageService(
    private val omoideStorageBackupRepository: OmoideStorageBackupRepository,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun execute(backupStrategy: BackupStrategy) =
        backupStrategy.run {
            try {
                logger.debug { "バックアップ先 : $externalStorageAbsolutePath" }
                // バックアップ先のパスがすでに登録済みであればスキップする
                if (omoideStorageBackupRepository.exists(externalStorageAbsolutePath)) {
                    logger.debug { "Skip: $externalStorageAbsolutePath is already backed up " }
                    return
                }

                // 3. Copy file
                if (!sourceAbsolutePath.exists()) {
                    logger.warn { "Source file not found: $sourceAbsolutePath" }
                    return
                }

                withContext(Dispatchers.IO) {
                    if (externalStorageAbsolutePath.parent != null && !Files.exists(externalStorageAbsolutePath.parent)) {
                        Files.createDirectories(externalStorageAbsolutePath.parent)
                    }
                    Files.copy(sourceAbsolutePath, externalStorageAbsolutePath, StandardCopyOption.REPLACE_EXISTING)
                    logger.debug { "$sourceAbsolutePath を $externalStorageAbsolutePath にコピー" }
                }

                // 4. Record to Database
                omoideStorageBackupRepository.saveBackupRecord(backupStrategy)

                logger.info { "Successfully backed up ${sourceAbsolutePath.name} to $externalStorageAbsolutePath" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to back up file: ${sourceAbsolutePath.name}" }
                // Do not throw so that the batch can continue
            }
        }
}
