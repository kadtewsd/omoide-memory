package com.kasakaid.omoidememory.downloader.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.downloader.service.ImportLocalFileService
import com.kasakaid.omoidememory.r2dbc.transaction.TransactionAttemptFailure
import com.kasakaid.omoidememory.r2dbc.transaction.TransactionExecutor
import com.kasakaid.omoidememory.utility.CoroutineHelper.mapWithCoroutine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "import-from-local")
class ImportFromLocal(
    private val importLocalFileService: ImportLocalFileService,
    private val transactionExecutor: TransactionExecutor,
    private val environment: Environment,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments): Unit =
        runBlocking {
            logger.info { "ローカルファイルからのインポート処理を開始します" }

            try {
                val sourceDir =
                    environment.getProperty("OMOIDE_BACKUP_DESTINATION")
                        ?: throw IllegalArgumentException("環境変数 OMOIDE_BACKUP_DESTINATION が設定されていません")

                logger.info { "対象ディレクトリ: $sourceDir" }

                // ディレクトリ配下の全ファイルを取得
                val localFiles = importLocalFileService.scanDirectory(Path.of(sourceDir))
                logger.info { "対象ファイル数: ${localFiles.size}件" }

                // 並列処理（セマフォで同時実行数を制限）
                localFiles.mapWithCoroutine(Semaphore(10)) { localFile ->
                    transactionExecutor
                        .executeWithPerLineLeftRollback(
                            requestId = "${localFile.name}:${localFile.path}",
                        ) {
                            importLocalFileService
                                .execute(localFile)
                                .onLeft {
                                    logger.error { "インポート失敗: ${localFile.name} - ${it.message}" }
                                }
                        }.fold(
                            ifLeft = {
                                when (it) {
                                    is TransactionAttemptFailure.Unmanaged -> {
                                        logger.error { "トランザクションエラー: ${localFile.name}" }
                                        logger.error { it.ex }
                                    }
                                }
                            },
                            ifRight = { result ->
                                when (result) {
                                    is ImportLocalFileService.ImportResult.Success -> {
                                        logger.debug { "インポート完了: ${localFile.name}" }
                                    }

                                    is ImportLocalFileService.ImportResult.Skip -> {
                                        logger.debug { "スキップ: ${localFile.name} - ${result.reason}" }
                                    }
                                }
                            },
                        )
                }

                logger.info { "ローカルファイルからのインポート処理を終了しました" }
            } catch (e: Exception) {
                logger.error(e) { "インポート処理中に致命的なエラーが発生しました" }
                throw e
            }
        }
}
