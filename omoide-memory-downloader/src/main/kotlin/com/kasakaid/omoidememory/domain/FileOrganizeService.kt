package com.kasakaid.omoidememory.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.regex.Pattern

/**
 * ダウンロードしてきたファイルを該当のパスに振り分ける
 */
object FileOrganizeService {

    private val logger = KotlinLogging.logger {}
    private val dateInFilenamePattern = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})")

    suspend fun organizeFile(file: OmoideMemory): Path = withContext(Dispatchers.IO) {
        val destinationRoot = System.getenv("OMOIDE_BACKUP_DESTINATION")
            ?: throw IllegalStateException("OMOIDE_BACKUP_DESTINATION is not set")

        val captureTime = file.captureTime
            ?: extractDateFromFilename(file.name)
            ?: LocalDateTime.ofInstant(Files.getLastModifiedTime(file.localPath).toInstant(), ZoneId.systemDefault())
                .atZone(ZoneId.systemDefault()).toOffsetDateTime()

        val year = captureTime.year.toString()
        val month = String.format("%02d", captureTime.monthValue)

        val targetDir = Path.of(
            destinationRoot, year, month,
            when (file) {
                is OmoideMemory.Video -> "video"
                is OmoideMemory.Photo -> "picture"
            }
        )
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir)
        }

        var targetFile = targetDir.resolve(file.name)
        var counter = 1

        /*
         * Simple duplicate handling: rename if exists.
         * Future/Design doc mentions hash check, but for now just rename to avoid overwrite if content differs,
         * or skip if content same (not implemented fully here as per design simplicity first).
         * Design says: "Compare hash... if same skip... if different rename".
         * For MVP, let's implement rename logic to be safe.
         */
        while (Files.exists(targetFile)) {
            // Check size as a poor man's hash first check?
            // Or just always rename for safety as per "If different rename"
            val nameWithoutExt = file.name.substringBeforeLast(".")
            val ext = file.name.substringAfterLast(".", "")
            val newName = if (ext.isNotEmpty()) "${nameWithoutExt}_$counter.$ext" else "${nameWithoutExt}_$counter"
            targetFile = targetDir.resolve(newName)
            counter++
        }

        logger.info { "Moving file ${file.name} to $targetFile" }
        Files.move(file.localPath, targetFile, StandardCopyOption.ATOMIC_MOVE)

        targetFile
    }

    private fun extractDateFromFilename(filename: String): OffsetDateTime? {
        val matcher = dateInFilenamePattern.matcher(filename)
        if (matcher.find()) {
            try {
                val year = matcher.group(1).toInt()
                val month = matcher.group(2).toInt()
                val day = matcher.group(3).toInt()
                return LocalDateTime.of(year, month, day, 12, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime()
            } catch (e: Exception) {
                // ignore
            }
        }
        return null
    }
}