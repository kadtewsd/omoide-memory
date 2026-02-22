package com.kasakaid.omoidememory.downloader.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kasakaid.omoidememory.domain.LocalFile
import com.kasakaid.omoidememory.domain.OmoideMemoryMetadataService
import com.kasakaid.omoidememory.downloader.domain.MediaType
import com.kasakaid.omoidememory.infrastructure.SyncedMemoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.toList

private val logger = KotlinLogging.logger {}

@Service
class ImportLocalFileService(
    private val omoideMemoryMetadataService: OmoideMemoryMetadataService,
    private val syncedMemoryRepository: SyncedMemoryRepository,
) {
    sealed interface ImportResult {
        object Success : ImportResult

        data class Skip(
            val reason: String,
        ) : ImportResult
    }

    data class ImportError(
        val message: String,
        val cause: Throwable? = null,
    )

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
    suspend fun execute(localFile: LocalFile): Either<ImportError, ImportResult> {
        return try {
            logger.info { "インポート開始: ${localFile.name}" }

            // メディアタイプを判定
            val mediaType =
                MediaType.of(localFile.name).getOrNull()
                    ?: return ImportResult.Skip("サポートされていないファイル形式").right()

            // 既にDBに存在するかチェック
            val exists =
                when (mediaType) {
                    MediaType.PHOTO -> syncedMemoryRepository.existsPhotoByFileName(localFile.name)
                    MediaType.VIDEO -> syncedMemoryRepository.existsVideoByFileName(localFile.name)
                }

            if (exists) {
                return ImportResult.Skip("ファイルは既に存在します").right()
            }

            // メタデータを抽出
            val omoideMemory =
                omoideMemoryMetadataService.extractOmoideMemoryFromLocalFile(
                    localFile = localFile,
                    mediaType = mediaType,
                )

            // DBに保存
            syncedMemoryRepository.save(omoideMemory)

            logger.info { "インポート完了: ${localFile.name} -> ${omoideMemory.localPath}" }

            ImportResult.Success.right()
        } catch (e: Exception) {
            logger.error(e) { "インポート中にエラーが発生: ${localFile.name}" }
            ImportError("インポート失敗", e).left()
        }
    }
}
