package com.kasakaid.omoidememory.backuplocal.service

import com.kasakaid.omoidememory.backuplocal.infrastructure.OmoideStorageBackupRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.name

@Service
class BackupLocalStorageService(
    private val omoideStorageBackupRepository: OmoideStorageBackupRepository,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun execute(
        targetPath: Path,
        localRoot: Path,
        externalRoot: Path,
    ) {
        try {
            // 1. Determine target path
            val relativePath = localRoot.relativize(targetPath)
            val determineTargetPath = externalRoot.resolve(relativePath)

            // 2. Check if it's already backed up
            val backupPathStr = determineTargetPath.toString()
            if (omoideStorageBackupRepository.exists(backupPathStr)) {
                logger.debug { "Skip: ${targetPath.name} is already backed up at $determineTargetPath." }
                return
            }

            // 3. Copy file
            if (!targetPath.exists()) {
                logger.warn { "Source file not found: $targetPath" }
                return
            }

            withContext(Dispatchers.IO) {
                if (determineTargetPath.parent != null && !Files.exists(determineTargetPath.parent)) {
                    Files.createDirectories(determineTargetPath.parent)
                }
                Files.copy(targetPath, determineTargetPath, StandardCopyOption.REPLACE_EXISTING)
            }

            // 4. Record to Database
            omoideStorageBackupRepository.saveBackupRecord(
                fileName = targetPath.name,
                backupPath = backupPathStr,
            )

            logger.info { "Successfully backed up ${targetPath.name} to $determineTargetPath" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to back up file: ${targetPath.name}" }
            // Do not throw so that the batch can continue
        }
    }
}
