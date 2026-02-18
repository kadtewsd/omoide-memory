package com.kasakaid.omoidememory.downloader.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "download-from-gdrive")
class DownloadFromGDrive(
    private val downloadFromGDrive: DownloadFromGDrive,
): ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        // 1. GDrive の直下から写真・動画を取得
        // 2. まずは永続化する
    }

}