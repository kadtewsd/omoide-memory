package com.kasakaid.omoidememory.downloader.service

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.domain.MediaType
import com.kasakaid.omoidememory.infrastructure.SyncedMemoryRepository
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.file.Path
import kotlin.io.path.name

/**
 * ダウンロードしてきたデータを永続化までもっていくサービス。
 */
@Service
class DownloadFileBackUpService(
    private val syncedMemoryRepository: SyncedMemoryRepository,
    private val driveService: DriveService,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun execute(
        googleFile: File,
        omoideBackupPath: Path,
        familyId: String,
        refreshToken: String,
    ): Either<DriveService.WriteError, FileIOFinish> =
        either {
            logger.info { "取得対象ファイル: ${googleFile.name} (Account: ${refreshToken.take(8)}...)" }

            // Check if already exists in DB to skip
            val type =
                MediaType.of(googleFile.name).getOrNull() ?: run {
                    return FileIOFinish
                        .Skip(
                            reason = "関連性のない拡張子なのでスキップ ${googleFile.name}",
                            filePath = Path.of(googleFile.name),
                        ).right()
                }

            when (type) {
                MediaType.PHOTO -> syncedMemoryRepository.existsPhotoByFileName(googleFile.name)
                MediaType.VIDEO -> syncedMemoryRepository.existsVideoByFileName(googleFile.name)
            }.let { exists ->
                if (exists) {
                    return FileIOFinish
                        .Skip(
                            reason = "ファイルは既に存在するためスキップし、ドライブ上から削除します: ${googleFile.name}",
                            filePath = Path.of(googleFile.name),
                        ).right()
                        .also {
                            driveService.delete(
                                fileId = googleFile.id,
                                refreshToken = refreshToken,
                            )
                        }
                }
            }

            val omoideMemory =
                driveService
                    .writeOmoideMemoryToTargetPath(
                        googleFile = googleFile,
                        omoideBackupPath = omoideBackupPath,
                        mediaType = type,
                        familyId = familyId,
                        refreshToken = refreshToken,
                    ).bind()

            // 6. DBに保存
            Either
                .catch {
                    syncedMemoryRepository.save(omoideMemory)
                    logger.info { "処理完了: ${googleFile.name} -> ${omoideMemory.localPath}" }
                    FileIOFinish.Success(omoideMemory.localPath)
                }.mapLeft {
                    logger.error {
                        "問題が発生したため永続化を行いませんでした ${googleFile.name}"
                    }
                    logger.error { OneLineLogFormatter.format(it) }
                    logger.error { it }
                    DriveService.WriteError(omoideMemory.localPath)
                }.map { successResult ->
                    driveService
                        .delete(googleFile.id, refreshToken)
                        .fold(
                            ifRight = {
                                logger.info { "Google Drive からの物理削除完了: ${googleFile.name} (ID: ${googleFile.id})" }
                                successResult
                            },
                            ifLeft = {
                                logger.error { "Google Drive からの物理削除失敗（ファイルは残ります）: ${googleFile.name} | Reason: ${it.message}" }
                                logger.error { OneLineLogFormatter.format(it) }
                                return DriveService.WriteError(omoideMemory.localPath).left()
                            },
                        )
                }.bind()
        }
}
