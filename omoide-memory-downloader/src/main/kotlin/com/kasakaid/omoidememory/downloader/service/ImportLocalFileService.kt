package com.kasakaid.omoidememory.downloader.service

import arrow.core.Either
import arrow.core.raise.context.bind
import arrow.core.raise.context.either
import arrow.core.right
import com.kasakaid.omoidememory.domain.LocalFile
import com.kasakaid.omoidememory.domain.MetadataExtractError
import com.kasakaid.omoidememory.domain.OmoideMemoryMetadataService
import com.kasakaid.omoidememory.downloader.domain.MediaType
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
    private val omoideMemoryMetadataService: OmoideMemoryMetadataService,
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
     * 単一ファイルのインポート処理
     */
    suspend fun execute(
        localFile: LocalFile,
        familyId: String,
    ): Either<MetadataExtractError, FileIOFinish> =
        either {
            logger.info { "インポート開始: ${localFile.name}" }

            // メディアタイプを判定
            val mediaType =
                MediaType.of(localFile.name).getOrNull()
                    ?: return FileIOFinish
                        .Skip(
                            reason = "サポートされていないファイル形式",
                            filePath = localFile.path,
                        ).right()

            // 既にDBに存在するかチェック
            val exists =
                when (mediaType) {
                    MediaType.PHOTO -> syncedMemoryRepository.existsPhotoByFileName(localFile.name)
                    MediaType.VIDEO -> syncedMemoryRepository.existsVideoByFileName(localFile.name)
                }

            if (exists) {
                return FileIOFinish
                    .Skip(
                        reason = "ファイルは既に存在します",
                        filePath = localFile.path,
                    ).right()
            }

            // メタデータを抽出
            val omoideMemory =
                omoideMemoryMetadataService
                    .extractOmoideMemoryFromLocalFile(
                        localFile = localFile,
                        mediaType = mediaType,
                        familyId = familyId,
                    ).bind()

            // DBに保存
            syncedMemoryRepository.save(omoideMemory)

            logger.info { "インポート完了: ${localFile.name} -> ${omoideMemory.localPath}" }
            FileIOFinish.Success(filePath = omoideMemory.localPath)
        }
}
