package com.kasakaid.omoidememory.patchtool.filedirmodification.service

import com.kasakaid.omoidememory.domain.FileOrganizeService
import com.kasakaid.omoidememory.domain.LocalFile
import com.kasakaid.omoidememory.patchtool.filedirmodification.domain.RecognizedPath
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
    suspend fun execute(
        files: List<RecognizedPath>,
        backupRootPath: LocalFile,
    ) = withContext(Dispatchers.IO) {
        for (file in files) {
            try {
                processFile(file, backupRootPath)
            } catch (e: Exception) {
                logger.error(e) { "ファイル ${file.fileName} の再編成に失敗しました。" }
            }
        }
        logger.info { "再編成処理を終了しました。" }
    }

    private suspend fun processFile(
        file: RecognizedPath,
        backupRootPath: LocalFile,
    ) {
        val fileName = file.fileName

        if (!file.isProcessable) {
            logger.warn { "ファイル名から撮影日時またはメディアタイプが推測できないためスキップします: $fileName" }
            return
        }

        val correctCaptureTime = file.correctCaptureTime!!
        val mediaType = file.mediaType!!

        val reorganizedFile =
            ReorganizedFile(
                fileName = fileName,
                mediaType = mediaType,
                captureTime = correctCaptureTime,
                backupRootPath = backupRootPath.path,
            )

        val currentPathAbsolute = file.path.toAbsolutePath()
        val correctPathAbsolute = reorganizedFile.correctPath.toAbsolutePath()

        if (currentPathAbsolute == correctPathAbsolute) {
            logger.debug { "ファイルは正しい位置にあります: $fileName" }

            // DB上の server_path や capture_time だけ間違っているケースに備え
            // パス移動が不要でもDBは更新しておく
            patchRepository.update(reorganizedFile)
            return
        }

        logger.info { "ファイルを移動します: $currentPathAbsolute -> $correctPathAbsolute" }

        // 3. ファイルを移動
        FileOrganizeService.moveToTarget(
            sourcePath = currentPathAbsolute,
            targetPath = correctPathAbsolute,
        )

        // 4. DBの情報を更新
        patchRepository.update(reorganizedFile)

        logger.info { "DBのパス情報と撮影日時を更新しました: $fileName" }
    }
}
