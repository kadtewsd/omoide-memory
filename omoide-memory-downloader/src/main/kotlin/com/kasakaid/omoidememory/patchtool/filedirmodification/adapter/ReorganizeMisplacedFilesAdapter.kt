package com.kasakaid.omoidememory.patchtool.filedirmodification.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.downloader.adapter.PostProcess
import com.kasakaid.omoidememory.patchtool.filedirmodification.service.ReorganizeMisplacedFilesService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "reorganize-misplaced-files")
class ReorganizeMisplacedFilesAdapter(
    private val reorganizeMisplacedFilesService: ReorganizeMisplacedFilesService,
    private val transactionalOperator: TransactionalOperator,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments): Unit =
        runBlocking {
            val destination =
                System.getenv("OMOIDE_BACKUP_DIRECTORY")
                    ?: throw IllegalArgumentException("OMOIDE_BACKUP_DIRECTORY 環境変数が設定されていません。")

            val targetYm =
                System.getenv("MODIFY_YM")
                    ?: throw IllegalArgumentException("MODIFY_YM 環境変数が設定されていません。")

            require(targetYm.length == 6 && targetYm.all { it.isDigit() }) {
                "MODIFY_YM は YYYYMM の形式で指定してください: $targetYm"
            }

            val year = targetYm.substring(0, 4)
            val month = targetYm.substring(4, 6)

            val backupRootPath = Path.of(destination)
            if (!backupRootPath.isAbsolute) {
                throw IllegalArgumentException("絶対パスを指定してください。")
            }

            val targetDir = backupRootPath.resolve(year).resolve(month)

            logger.info { "取り込み済みファイルの再編成を開始します。対象ディレクトリ: $targetDir" }

            if (!java.nio.file.Files
                    .exists(targetDir)
            ) {
                logger.warn { "対象ディレクトリが存在しません: $targetDir" }
                return@runBlocking
            }

            val filesToProcess =
                listOf("photo", "video")
                    .map { targetDir.resolve(it) }
                    .filter {
                        java.nio.file.Files
                            .exists(it)
                    }.flatMap { mediaDir ->
                        java.nio.file.Files.list(mediaDir).use { stream ->
                            stream
                                .filter {
                                    java.nio.file.Files
                                        .isRegularFile(it)
                                }.toList()
                        }
                    }.map {
                        com.kasakaid.omoidememory.patchtool.filedirmodification.domain
                            .RecognizedPath(it)
                    }

            logger.info { "対象ディレクトリ内で ${filesToProcess.size} 件のファイルが見つかりました。" }

            transactionalOperator.executeAndAwait {
                reorganizeMisplacedFilesService.execute(
                    files = filesToProcess,
                    backupRootPath =
                        com.kasakaid.omoidememory.domain
                            .LocalFile(backupRootPath, backupRootPath.fileName.toString()),
                )
            }

            PostProcess.finish()
            logger.info { "全ての再編成処理を終了しました。" }
        }
}
