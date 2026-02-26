package com.kasakaid.omoidememory.downloader.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.service.ImportLocalFileService
import com.kasakaid.omoidememory.r2dbc.transaction.RollbackException
import com.kasakaid.omoidememory.utility.CoroutineHelper.mapWithCoroutine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.core.env.getProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.executeAndAwait
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "import-from-local")
class ImportFromLocal(
    private val importLocalFileService: ImportLocalFileService,
    private val transactionalOperator: org.springframework.transaction.reactive.TransactionalOperator,
    private val environment: Environment,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments): Unit =
        runBlocking {
            logger.info { "ローカルファイルからのインポート処理を開始します" }
            val sourceDir =
                environment.getProperty("OMOIDE_BACKUP_DIRECTORY")
                    ?: throw IllegalArgumentException("環境変数 OMOIDE_BACKUP_DIRECTORY が設定されていません")

            val familyId =
                environment.getProperty("GDRIVE_FOLDER_ID")
                    ?: throw IllegalArgumentException("環境変数 GDRIVE_FOLDER_ID が設定されていません")
            logger.info { "対象ディレクトリ: $sourceDir" }

            // ディレクトリ配下の全ファイルを取得
            val localFiles = importLocalFileService.scanDirectory(Path.of(sourceDir))
            logger.info { "対象ファイル数: ${localFiles.size}件" }

            // 並列処理（セマフォで同時実行数を制限）
            localFiles.mapWithCoroutine(Semaphore(10)) { localFile ->
                try {
                    transactionalOperator.executeAndAwait {
                        // ReactiveTransaction が引数で入ってくるが、Repository などに渡す必要なし
                        // Spring の TransactionalOperator は、トランザクション情報を Reactor Context という「目に見えない箱」に入れて、リアクティブなパイプライン（Flux/Mono）の上流から下流まで伝播させます。
                        importLocalFileService
                            .execute(localFile, familyId)
                            .onRight {
                                PostProcess.onSuccess(it)
                            }.onLeft {
                                throw RollbackException(it)
                            }
                    }
                } catch (e: RollbackException) {
                    val error = e.leftValue
                    when (error) {
                        is DriveService.WriteError -> PostProcess.onFailure(error)
                        else -> PostProcess.onUnmanaged(e)
                    }
                }
            }
            logger.info { "ローカルファイルからのインポート処理を終了しました" }
        }
}
