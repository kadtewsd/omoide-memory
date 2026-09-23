package com.kasakaid.omoidememory.importfromlocal.service

import arrow.core.Either
import arrow.core.right
import com.kasakaid.omoidememory.domain.FileOrganizeService
import com.kasakaid.omoidememory.domain.LocalFile
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.domain.SourceFile
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.domain.MediaType
import com.kasakaid.omoidememory.downloader.domain.OmoideMemoryFactory
import com.kasakaid.omoidememory.downloader.service.FileIOFinish
import com.kasakaid.omoidememory.infrastructure.SyncedMemoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

private val logger = KotlinLogging.logger {}

@Service
class ImportLocalFileService(
    private val syncedMemoryRepository: SyncedMemoryRepository,
    private val omoideMemoryFactory: OmoideMemoryFactory,
) {
    /**
     * 指定ディレクトリ配下の全ファイルを再帰的に走査
     */
    suspend fun scanDirectory(directoryPath: Path): List<LocalFile> {
        require(Files.exists(directoryPath)) { "指定されたディレクトリが存在しません: $directoryPath" }
        require(Files.isDirectory(directoryPath)) { "指定されたパスはディレクトリではありません: $directoryPath" }

        logger.info { "ディレクトリ走査開始: $directoryPath" }

        return Files
            .walk(directoryPath)
            .filter { it.isRegularFile() }
            .filter { MediaType.of(it.name).isSome() }
            .map { path ->
                LocalFile(
                    path = path,
                    name = path.fileName.toString(),
                )
            }.toList()
            .also { logger.info { "対象ファイル ${it.size}件を検出しました" } }
    }

    /**
     * 単一ファイルのインポート処理。
     *
     * [importMode] によって動作が切り替わる。
     * - [ImportMode.DbMaintenance]: ファイルを再配置せず、取込元パスのままDBに登録する
     * - [ImportMode.FileImport]: GDrive 側と同じ配置ルールで omoideBackupPath 配下にコピーしてDBに登録する
     */
    suspend fun execute(
        localFile: LocalFile,
        familyId: String,
        importMode: ImportMode,
    ): Either<DriveService.WriteError, FileIOFinish> {
        logger.info { "インポート開始: ${localFile.name}" }

        val mediaType =
            MediaType.of(localFile.name).getOrNull()
                ?: return FileIOFinish
                    .Skip(
                        reason = "サポートされていないファイル形式",
                        filePath = localFile.path,
                    ).right()

        val exists =
            when (mediaType) {
                MediaType.PHOTO -> syncedMemoryRepository.existsPhotoByFileName(localFile.name)
                MediaType.VIDEO -> syncedMemoryRepository.existsVideoByFileName(localFile.name)
            }

        if (exists) {
            syncedMemoryRepository.deleteByFileName(localFile.name)
        }

        val omoideMemory: Either<DriveService.WriteError, OmoideMemory> =
            omoideMemoryFactory.createOmoideMemoryFrom(
                sourcePath = localFile.path,
                sourceFile = SourceFile.fromLocalFile(localFile.path),
                omoideBackupPath = importMode.omoideBackupPath,
                mediaType = mediaType,
                familyId = familyId,
            )

        return omoideMemory.map { omoideMemory ->
            syncedMemoryRepository.save(omoideMemory)

            when (importMode) {
                is ImportMode.DbMaintenance -> {
                    logger.debug { "Maintenance モードであるのでファイルの移動は実施しません" }
                }

                is ImportMode.FileImport -> {
                    FileOrganizeService.copyToTarget(
                        sourcePath = localFile.path,
                        targetPath = omoideMemory.localPath,
                    )
                }
            }
            logger.info { "インポート完了: ${localFile.name} -> ${omoideMemory.localPath}" }
            FileIOFinish.Success(filePath = omoideMemory.localPath)
        }
    }
}
