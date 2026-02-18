package com.kasakaid.omoidememory.downloader.adapter

import arrow.core.right
import com.kasakaid.omoidememor.r2dbc.transaction.TransactionExecutor
import com.kasakaid.omoidememor.utility.CoroutineHelper.mapWithCoroutine
import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.service.DownloadFileBackUpService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "download-from-gdrive")
class DownloadFromGDrive(
    private val driveService: DriveService,
    private val downloadFileBackUpService: DownloadFileBackUpService,
    private val transactionExecutor: TransactionExecutor,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments): Unit = runBlocking {
        logger.info { "Google Driveからのダウンロード処理を開始します" }
        // 1. Google Driveから対象フォルダ配下の全ファイルを取得
        val omoideMemories: List<OmoideMemory> = driveService.listFiles()
        omoideMemories.mapWithCoroutine(Semaphore(300)) { omoideMemory ->
            transactionExecutor.executeWithPerLineLeftRollback(
                "${omoideMemory.name}:${omoideMemory.driveFileId}"
            ) {
                downloadFileBackUpService.execute(omoideMemory)
                omoideMemory.right()
            }
        }
    }
}
