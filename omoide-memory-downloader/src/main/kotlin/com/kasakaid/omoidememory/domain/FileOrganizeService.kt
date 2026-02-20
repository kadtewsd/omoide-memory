package com.kasakaid.omoidememory.domain

import com.kasakaid.omoidememory.downloader.domain.MediaType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * ファイルの配置先パスを決定する（実際の移動は行わない）
     *
     * @param fileName ファイル名
     * @param captureTime 撮影日時
     * @return 配置先のフルパス
     */
    suspend fun determineTargetPath(fileName: String, captureTime: OffsetDateTime?): Path =
        withContext(Dispatchers.IO) {
            val destinationRoot = System.getenv("OMOIDE_BACKUP_DESTINATION")
                ?: throw IllegalStateException("OMOIDE_BACKUP_DESTINATION is not set")

            val effectiveCaptureTime = captureTime
                ?: extractDateFromFilename(fileName)
                ?: OffsetDateTime.now()

            val year = effectiveCaptureTime.year.toString()
            val month = String.format("%02d", effectiveCaptureTime.monthValue)

            val contentType = MediaType.of(fileName).getOrNull()
                ?: throw IllegalArgumentException("サポートされていないファイル形式: $fileName")

            val targetDir = Path.of(destinationRoot, year, month, contentType.directoryName)

            // ディレクトリが存在しない場合は作成
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir)
            }

            var targetFile = targetDir.resolve(fileName)
            var counter = 1

            // ファイルが既に存在する場合は連番を付与
            while (Files.exists(targetFile)) {
                targetFile = targetDir.resolve(FileStructure.of(fileName).withCounter(counter++))
            }

            targetFile
        }

    /**
     * ファイルを最終的な配置先に移動する
     *
     * @param sourcePath 移動元のパス（ダウンロード済みの一時ファイル）
     * @param targetPath 移動先のパス
     */
    suspend fun moveToTarget(sourcePath: Path, targetPath: Path): Path = withContext(Dispatchers.IO) {
        logger.info { "ファイル移動: ${sourcePath.fileName} → $targetPath" }

        // 親ディレクトリが存在しない場合は作成
        if (targetPath.parent != null && !Files.exists(targetPath.parent)) {
            Files.createDirectories(targetPath.parent)
        }

        Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE)
        targetPath
    }

    private fun extractDateFromFilename(filename: String): OffsetDateTime? {
        val matcher = dateInFilenamePattern.matcher(filename)
        if (matcher.find()) {
            try {
                val year = matcher.group(1).toInt()
                val month = matcher.group(2).toInt()
                val day = matcher.group(3).toInt()
                return LocalDateTime.of(year, month, day, 12, 0)
                    .atZone(ZoneId.systemDefault())
                    .toOffsetDateTime()
            } catch (e: Exception) {
                logger.warn { "ファイル名からの日付抽出に失敗: $filename" }
            }
        }
        return null
    }
}