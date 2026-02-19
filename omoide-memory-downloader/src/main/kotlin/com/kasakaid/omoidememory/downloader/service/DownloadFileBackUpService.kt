package com.kasakaid.omoidememory.downloader.service

import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.infrastructure.SyncedMemoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

/**
 * ダウンロードしてきたデータを永続化までもっていくサービス。
 */
@Service
class DownloadFileBackUpService(
    private val syncedMemoryRepository: SyncedMemoryRepository,
    private val driveService: DriveService,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun execute(omoideMemory: OmoideMemory) {

        logger.info { "取得対象ファイル: ${omoideMemory.name}" }

        // Check if already exists in DB to skip
        val exists = when (omoideMemory) {
            is OmoideMemory.Photo -> syncedMemoryRepository.existsPhotoByFileName(omoideMemory.name)
            is OmoideMemory.Video -> syncedMemoryRepository.existsVideoByFileName(omoideMemory.name)
        }

        if (exists) {
            logger.info { "ファイルは既に存在するためスキップします: ${omoideMemory.name}" }
            return
        }

        // 2. ファイルをダウンロード
        driveService.writeOmoideMemoryToTargetPath(omoideMemory)

        // 5. ファイル配置 (FileOrganizeService) is not imported/used in recent user snippet logic flow clearly for 'OmoideMemory'
        // If organization is needed, it should probably happen or return a new path.
        // But user instructions were "Google のオブジェクトを直接ドメインオブジェクトに変更して永続化しています"
        // So I will assume the path in OmoideMemory (set by translator) is the final one, or near final.
        // For now, persist the OmoideMemory as-is.

        // 6. DBに保存
        syncedMemoryRepository.save(omoideMemory)

        logger.info { "処理完了: ${omoideMemory.name} -> ${omoideMemory.localPath}" }
    }
}