package com.kasakaid.omoidememory.backuplocal.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.backuplocal.infrastructure.OmoideStorageBackupRepository
import com.kasakaid.omoidememory.backuplocal.service.BackupLocalStorageService
import com.kasakaid.omoidememory.utility.CoroutineHelper.mapWithCoroutine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.nio.file.Files
import java.nio.file.Path

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "backup-to-local")
class BackupLocalStorageAdapter(
    private val backupLocalStorageService: BackupLocalStorageService,
    private val omoideStorageBackupRepository: OmoideStorageBackupRepository,
    private val transactionalOperator: TransactionalOperator,
) : ApplicationRunner {
    private val logger = KotlinLogging.logger {}

    override fun run(args: ApplicationArguments): Unit =
        runBlocking {
            logger.info { "ローカルドライブへのバックアップ処理を開始します。外界のチェックを Adapter 層で実施！" }

            // 1. 環境変数・ドライブ接続チェック
            val omoideBackupDirectory =
                System.getenv("OMOIDE_BACKUP_DIRECTORY")
                    ?: throw IllegalStateException("環境変数 OMOIDE_BACKUP_DIRECTORY が設定されていません。")

            val destinationPath = Path.of(omoideBackupDirectory)
            if (!Files.exists(destinationPath) || !Files.isDirectory(destinationPath)) {
                throw IllegalStateException("指定されたバックアップ先ディレクトリが存在しません（ドライブ未接続）: $destinationPath")
            }

            if (!Files.isWritable(destinationPath)) {
                throw IllegalStateException("指定されたバックアップ先ディレクトリに書き込み権限がありません: $destinationPath")
            }

            // 2. バックアップ対象ファイルの取得
            val targets = omoideStorageBackupRepository.findBackupTargets().toList()
            logger.info { "バックアップ対象ファイル ${targets.size} 件を処理します" }

            // 並列度を指定して実行
            targets.mapWithCoroutine(Semaphore(10)) { target ->
                transactionalOperator.executeAndAwait {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.slf4j.MDCContext(mapOf("requestId" to target.id.toString()))) {
                        backupLocalStorageService.execute(target, destinationPath)
                    }
                }
            }

            logger.info { "ローカルドライブへのバックアップ処理を終了しました。" }
        }
}
