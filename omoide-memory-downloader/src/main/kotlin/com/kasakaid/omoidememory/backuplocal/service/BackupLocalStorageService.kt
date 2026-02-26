package com.kasakaid.omoidememory.backuplocal.service

import com.kasakaid.omoidememory.backuplocal.infrastructure.BackUpKey
import com.kasakaid.omoidememory.backuplocal.infrastructure.BackupTarget
import com.kasakaid.omoidememory.backuplocal.infrastructure.OmoideStorageBackupRepository
import com.kasakaid.omoidememory.domain.FileOrganizeService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

@Service
class BackupLocalStorageService(
    private val omoideStorageBackupRepository: OmoideStorageBackupRepository,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun execute(
        target: BackupTarget,
        omoideBackupDirectory: Path,
    ) {
        try {
            // 1. Determine target path
            val determineTargetPath = FileOrganizeService.determineTargetPath(target.fileName, target.captureTime, omoideBackupDirectory)

            // 2. Check if it's already backed up
            val key = BackUpKey(target.id, determineTargetPath)
            if (omoideStorageBackupRepository.exists(key)) {
                logger.debug { "Skip: ${target.fileName} is already backed up at $determineTargetPath." }
                return
            }

            // 3. Copy file
            val sourcePath = Path.of(target.serverPath)
            if (!sourcePath.exists()) {
                logger.warn { "Source file not found: ${target.serverPath}" }
                return
            }

            withContext(Dispatchers.IO) {
                if (determineTargetPath.parent != null && !Files.exists(determineTargetPath.parent)) {
                    Files.createDirectories(determineTargetPath.parent)
                }
                Files.copy(sourcePath, determineTargetPath, StandardCopyOption.REPLACE_EXISTING)
            }

            // 4. Record to Database
            omoideStorageBackupRepository.saveBackupRecord(
                sourceId = target.id,
                fileName = target.fileName,
                backupPath = determineTargetPath.toString(),
            )

            logger.info { "Successfully backed up ${target.fileName} to $determineTargetPath" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to back up file: ${target.fileName}" }
            // Do not throw so that the batch can continue
        }
    }
}
