package com.kasakaid.omoidememory.downloader.service

import com.kasakaid.omoidememory.domain.FileOrganizeService
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.logger
import com.kasakaid.omoidememory.infrastructure.SyncedMemoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging

class DownloadFileBackUpService(
    private val fileOrganizeService: FileOrganizeService,
    private val syncedMemoryRepository: SyncedMemoryRepository,
    private val driveService: DriveService,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun execute(omoideMemory: OmoideMemory) {

        logger.info { "取得対象ファイル: ${omoideMemory.name}" }

        // Check if already exists in DB to skip download
        when (omoideMemory) {
            is OmoideMemory.Photo ->  syncedMemoryRepository.existsPhotoByFileName(omoideMemory.name)
            is OmoideMemory.Video -> syncedMemoryRepository.existsVideoByFileName(omoideMemory.name)
        }.let {
            if (it) {
                return
            }
        }

        // 2. ファイルをダウンロード
        val downloadedFile = driveService.downloadFile(driveFile)

        // 3. メタデータを取得
        val metadata = metadataService.extractMetadata(downloadedFile)

        // 4. 位置情報から地名を取得（緯度経度がある場合のみ）
        // TODO: Optimize to not call API if near previous location in same batch
        val locationName = if (metadata.latitude != null && metadata.longitude != null) {
            locationService.getLocationName(metadata.latitude, metadata.longitude)
        } else {
            null
        }

        // 5. ファイルを年/月/picture or video の構造に配置
        val finalPath = fileOrganizeService.organizeFile(downloadedFile, metadata)

        // 6. DBに保存（jOOQ + R2DBC）
        syncedMemoryRepository.save(
            fileName = finalPath.fileName.toString(), // Use actual saved filename in case of rename
            serverPath = finalPath.toString(),
            metadata = metadata,
            locationName = locationName
        )

        // 7. Google Drive上のファイルを削除
        // Commented out for safety during initial testing phases
        // googleDriveService.deleteFile(driveFile.id)
        logger.info { "[DRY RUN] Would delete file from GDrive: ${driveFile.name}" }

        logger.info { "処理完了: ${downloadedFile.name} -> $finalPath" }

    }
}