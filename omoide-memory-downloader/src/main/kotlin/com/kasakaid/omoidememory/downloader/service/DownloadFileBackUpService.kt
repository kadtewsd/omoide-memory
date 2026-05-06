package com.kasakaid.omoidememory.downloader.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import com.kasakaid.omoidememory.domain.SourceFile
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.domain.MediaType
import com.kasakaid.omoidememory.downloader.domain.OmoideMemoryExportService
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * ダウンロードしてきたデータを永続化までもっていくサービス。
 */
class DownloadFileBackUpService(
    private val syncedMemoryRepository: OmoideMemoryRepository,
    private val omoideMemoryExportService: OmoideMemoryExportService,
    private val driveService: DriveService,
    private val omoideBackupPath: Path,
) {
    private val logger = KotlinLogging.logger {}
    private val tempDir = Files.createTempDirectory(UUID.randomUUID().toString().replace("-", ""))

    init {
        logger.info { "一時フォルダ : $tempDir" }
    }

    suspend fun execute(
        sourceFile: SourceFile,
        familyId: String,
    ): Either<DriveService.WriteError, FileIOFinish> =
        either {
            logger.info { "取得対象ファイル: ${sourceFile.name}" }

            // Check if already exists in DB to skip
            val type =
                MediaType.of(sourceFile.name).getOrNull() ?: run {
                    return FileIOFinish
                        .Skip(
                            reason = "関連性のない拡張子なのでスキップ ${sourceFile.name}",
                            filePath = Path.of(sourceFile.name),
                        ).right()
                }

            when (type) {
                MediaType.PHOTO -> syncedMemoryRepository.existsPhotoByFileName(sourceFile.name)
                MediaType.VIDEO -> syncedMemoryRepository.existsVideoByFileName(sourceFile.name)
            }.let { exists ->
                if (exists) {
                    return FileIOFinish
                        .Skip(
                            reason = "ファイルは既に存在するためスキップします: ${sourceFile.name}",
                            filePath = Path.of(sourceFile.name),
                        ).right()
                }
            }

            val tempPath = tempDir.resolve(sourceFile.name)
            try {
                logger.debug { "Gdrive からのダウンロード開始 ->  $tempPath" }
                Files
                    .newOutputStream(tempPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                    .use { outputStream ->
                        driveService.download(sourceFile.googleFileId!!, outputStream).onLeft {
                            throw it
                        }
                    }

                val omoideMemory =
                    omoideMemoryExportService
                        .export(
                            tempPath = tempPath,
                            fileName = sourceFile.name,
                            sourceFile = sourceFile,
                            omoideBackupPath = omoideBackupPath,
                            mediaType = type,
                            familyId = familyId,
                        ).bind()

                // 6. DBに保存
                Either
                    .catch {
                        syncedMemoryRepository.save(omoideMemory)
                        logger.info { "処理完了: ${sourceFile.name} -> ${omoideMemory.localPath}" }
                        FileIOFinish.Success(omoideMemory.localPath)
                    }.mapLeft {
                        logger.error {
                            "問題が発生したため永続化を行いませんでした ${sourceFile.name}"
                        }
                        logger.error { OneLineLogFormatter.format(it) }
                        logger.error { it }
                        DriveService.WriteError(omoideMemory.localPath)
                    }.bind()
            } finally {
                Files.deleteIfExists(tempPath)
            }
        }

    fun finalize() {
        Files.deleteIfExists(tempDir)
    }
}
