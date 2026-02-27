package com.kasakaid.omoidememory.backuplocal.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
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
    private val transactionalOperator: TransactionalOperator,
) : ApplicationRunner {
    private val logger = KotlinLogging.logger {}

    override fun run(args: ApplicationArguments): Unit =
        runBlocking {
            logger.info { "外付けドライブへのバックアップ処理を開始します。" }

            // 1-a. ローカルスキャン元のチェック
            val localRootEnv =
                System.getenv("OMOIDE_BACKUP_DIRECTORY")
                    ?: throw IllegalStateException("環境変数 OMOIDE_BACKUP_DIRECTORY が設定されていません。")

            val localRoot = Path.of(localRootEnv)
            if (!Files.exists(localRoot) || !Files.isDirectory(localRoot)) {
                throw IllegalStateException("スキャン元ディレクトリが存在しません: $localRoot")
            }
            if (!Files.isReadable(localRoot)) {
                throw IllegalStateException("スキャン元ディレクトリを読み取れません: $localRoot")
            }
            logger.info { "スキャン元: $localRoot" }

            // 1-b. 外付けバックアップ先のチェック
            val externalRootEnv =
                System.getenv("EXTERNAL_STORAGE_BACKUP_DIRECTORY")
                    ?: throw IllegalStateException("環境変数 EXTERNAL_STORAGE_BACKUP_DIRECTORY が設定されていません。")

            val externalRoot = Path.of(externalRootEnv)
            if (!Files.exists(externalRoot) || !Files.isDirectory(externalRoot)) {
                throw IllegalStateException("外付けドライブのバックアップ先が存在しません（ドライブ未接続）: $externalRoot")
            }
            if (!Files.isWritable(externalRoot)) {
                throw IllegalStateException("外付けドライブのバックアップ先に書き込み権限がありません: $externalRoot")
            }
            logger.info { "バックアップ先: $externalRoot" }

            // 2. ローカルディレクトリの再帰スキャン（DB 参照なし）
            val targets: List<Path> =
                Files
                    .walk(localRoot)
                    .filter { Files.isRegularFile(it) }
                    .toList()

            logger.info { "バックアップ対象ファイル ${targets.size} 件を処理します" }

            // 並列度を指定して実行
            targets.mapWithCoroutine(Semaphore(10)) { targetPath ->
                transactionalOperator.executeAndAwait {
                    backupLocalStorageService.execute(targetPath, localRoot, externalRoot)
                }
            }

            logger.info { "外付けドライブへのバックアップ処理を終了しました。" }
        }
}
