package com.kasakaid.omoidememory.importfromlocal.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.downloader.adapter.PostProcess
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.importfromlocal.service.ImportLocalFileService
import com.kasakaid.omoidememory.importfromlocal.service.ImportMode
import com.kasakaid.omoidememory.r2dbc.transaction.RollbackException
import com.kasakaid.omoidememory.utility.CoroutineHelper.mapWithCoroutine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "import-from-local")
class ImportFromLocal(
    private val importLocalFileService: ImportLocalFileService,
    private val transactionalOperator: TransactionalOperator,
    private val environment: Environment,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments): Unit =
        runBlocking {
            logger.info { "ローカルファイルからのインポート処理を開始します" }

            val familyId =
                environment.getProperty("OMOIDE_FAMILY_ID")
                    ?: throw IllegalArgumentException("環境変数 OMOIDE_FAMILY_ID が設定されていません")

            val sourceDir =
                environment.getProperty("OMOIDE_SOURCE_DIRECTORY")
                    ?: throw IllegalArgumentException("環境変数 OMOIDE_SOURCE_DIRECTORY が設定されていません")

            // OMOIDE_BACKUP_DIRECTORY の有無で動作モードを決定する。
            // 未設定の場合は DBメンテナンスモード:
            //   ファイルの再配置は行わず、取込元パスのままメタデータを抽出してDBに登録する。
            //   不正なデータや重複データが生じた際に DB のレコードを洗い替えする際に使用する。
            // 設定済みの場合は ファイル取り込みモード:
            //   取込元ファイルを OMOIDE_BACKUP_DIRECTORY 配下の正式な格納先（GDrive 側と同じ配置ルール）に
            //   コピーし、コピー先のパスを DB に登録する。
            val importMode =
                environment
                    .getProperty("OMOIDE_BACKUP_DIRECTORY")
                    ?.let { ImportMode.FileImport(omoideBackupPath = Path.of(it)) }
                    ?: ImportMode.DbMaintenance(omoideBackupPath = Path.of(sourceDir))

            when (importMode) {
                is ImportMode.DbMaintenance -> {
                    logger.info { "モード: DBメンテナンス（ファイル再配置なし）/ 取込元ディレクトリ: $sourceDir" }
                }

                is ImportMode.FileImport -> {
                    logger.info { "モード: ファイル取り込み / 取込元ディレクトリ: $sourceDir / バックアップ先: ${importMode.omoideBackupPath}" }
                }
            }

            // 取込元ディレクトリ配下の全ファイルを取得
            val localFiles = importLocalFileService.scanDirectory(Path.of(sourceDir))
            logger.info { "対象ファイル数: ${localFiles.size}件" }

            // 並列処理（セマフォで同時実行数を制限）
            localFiles.mapWithCoroutine(Semaphore(10)) { localFile ->
                try {
                    transactionalOperator.executeAndAwait {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.slf4j.MDCContext(mapOf("requestId" to localFile.name))) {
                            // ReactiveTransaction が引数で入ってくるが、Repository などに渡す必要なし
                            // Spring の TransactionalOperator は、トランザクション情報を Reactor Context という「目に見えない箱」に入れて、リアクティブなパイプライン（Flux/Mono）の上流から下流まで伝播させます。
                            importLocalFileService
                                .execute(
                                    localFile = localFile,
                                    familyId = familyId,
                                    importMode = importMode,
                                ).onRight {
                                    PostProcess.onSuccess(it)
                                }.onLeft {
                                    throw RollbackException(it)
                                }
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
