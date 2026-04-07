package com.kasakaid.omoidememory.downloader.service

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import com.kasakaid.omoidememory.domain.SourceFile
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.domain.MediaType
import com.kasakaid.omoidememory.downloader.domain.OmoideMemoryExportService
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

/**
 * ダウンロードしてきたデータを永続化までもっていくサービス。
 */
class DownloadFileBackUpService(
    private val syncedMemoryRepository: OmoideMemoryRepository,
    private val omoideMemoryExportService: OmoideMemoryExportService,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun execute(
        tempPath: Path,
        fileName: String,
        sourceFile: SourceFile,
        omoideBackupPath: Path,
        familyId: String,
    ): Either<DriveService.WriteError, FileIOFinish> =
        either {
            logger.info { "取得対象ファイル: $fileName" }

            // Check if already exists in DB to skip
            val type =
                MediaType.of(fileName).getOrNull() ?: run {
                    return FileIOFinish
                        .Skip(
                            reason = "関連性のない拡張子なのでスキップ $fileName",
                            filePath = Path.of(fileName),
                        ).right()
                }

            when (type) {
                MediaType.PHOTO -> syncedMemoryRepository.existsPhotoByFileName(fileName)
                MediaType.VIDEO -> syncedMemoryRepository.existsVideoByFileName(fileName)
            }.let { exists ->
                if (exists) {
                    return FileIOFinish
                        .Skip(
                            reason = "ファイルは既に存在するためスキップします: $fileName",
                            filePath = Path.of(fileName),
                        ).right()
                }
            }

            val omoideMemory =
                omoideMemoryExportService
                    .export(
                        tempPath = tempPath,
                        fileName = fileName,
                        sourceFile = sourceFile,
                        omoideBackupPath = omoideBackupPath,
                        mediaType = type,
                        familyId = familyId,
                    ).bind()

            // 6. DBに保存
            Either
                .catch {
                    syncedMemoryRepository.save(omoideMemory)
                    logger.info { "処理完了: $fileName -> ${omoideMemory.localPath}" }
                    FileIOFinish.Success(omoideMemory.localPath)
                }.mapLeft {
                    logger.error {
                        "問題が発生したため永続化を行いませんでした $fileName"
                    }
                    logger.error { OneLineLogFormatter.format(it) }
                    logger.error { it }
                    DriveService.WriteError(omoideMemory.localPath)
                }.bind()
        }
}
