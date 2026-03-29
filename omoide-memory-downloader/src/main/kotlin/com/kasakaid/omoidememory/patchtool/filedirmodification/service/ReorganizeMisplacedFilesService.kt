package com.kasakaid.omoidememory.patchtool.filedirmodification.service

import com.kasakaid.omoidememory.domain.FileOrganizeService
import com.kasakaid.omoidememory.patchtool.filedirmodification.domain.ReorganizeMisplacedFilesRepository
import com.kasakaid.omoidememory.patchtool.filedirmodification.domain.ReorganizedFile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ReorganizeMisplacedFilesService(
    private val patchRepository: ReorganizeMisplacedFilesRepository,
) {
    suspend fun execute(files: List<ReorganizedFile>) =
        withContext(Dispatchers.IO) {
            for (file in files) {
                try {
                    processFile(file)
                } catch (e: Exception) {
                    logger.error(e) { "ファイル ${file.fileName} の再編成に失敗しました。" }
                }
            }
            logger.info { "再編成処理を終了しました。" }
        }

    private suspend fun processFile(file: ReorganizedFile) {
        val fileName = file.fileName

        if (!file.isProcessable) {
            logger.warn { "ファイル名から撮影日時またはメディアタイプが推測できないためスキップします: $fileName" }
            return
        }

        file.correctByFileName().map { correctedFile ->
            // 3. ファイルを移動
            FileOrganizeService.moveToTarget(
                sourcePath = file.filePath.toAbsolutePath(),
                targetPath = correctedFile.filePath.toAbsolutePath(),
            )

            // 4. DBの情報を更新
            patchRepository.update(correctedFile)

            logger.info { "DBのパス情報と撮影日時を更新しました: $fileName" }
        }
    }
}
