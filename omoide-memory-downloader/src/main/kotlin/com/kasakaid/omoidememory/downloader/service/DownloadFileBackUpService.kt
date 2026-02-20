package com.kasakaid.omoidememory.downloader.service

import arrow.core.raise.either
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.domain.MediaType
import com.kasakaid.omoidememory.infrastructure.SyncedMemoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.nio.file.Path

/**
 * ダウンロードしてきたデータを永続化までもっていくサービス。
 */
@Service
class DownloadFileBackUpService(
    private val syncedMemoryRepository: SyncedMemoryRepository,
    private val driveService: DriveService,
    private val environment: Environment,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun execute(
        googleFile: File,
        omoideBackupPath: Path,
    ) {
        logger.info { "取得対象ファイル: ${googleFile.name}" }

        // Check if already exists in DB to skip
        val type =
            MediaType.of(googleFile.name).getOrNull() ?: run {
                logger.warn { "関連性のない拡張子なのでスキップ ${googleFile.name}" }
                return
            }

        when (type) {
            MediaType.PHOTO -> syncedMemoryRepository.existsPhotoByFileName(googleFile.name)
            MediaType.VIDEO -> syncedMemoryRepository.existsVideoByFileName(googleFile.name)
        }.let { exists ->
            if (exists) {
                logger.info { "ファイルは既に存在するためスキップします: ${googleFile.name}" }
                return
            }
        }

        either {
            val omoideMemory =
                driveService
                    .writeOmoideMemoryToTargetPath(
                        googleFile = googleFile,
                        omoideBackupPath = omoideBackupPath,
                        mediaType = type,
                    ).bind()

            // 6. DBに保存
            syncedMemoryRepository.save(omoideMemory)

            logger.info { "処理完了: ${googleFile.name} -> ${omoideMemory.localPath}" }
        }.onLeft {
            logger.error { "問題が発生したため永続化を行いませんでした ${googleFile.name}" }
        }
    }
}
