package com.kasakaid.omoidememory

import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder

const val APPLICATION_RUNNER_KEY = "runner_name"

@SpringBootApplication
class OmoideMemoryDownloader

fun main(args: Array<String>) {

    val destination = System.getenv("OMOIDE_BACKUP_DESTINATION")

    if (destination.isNullOrBlank()) {
        throw IllegalArgumentException("ダウンロードファイルの保存先を指定してください。")
    }

    val gdriveAccount = System.getenv("OMOIDE_GDRIVE_CREDENTIALS_PATH")
    if (gdriveAccount.isNullOrBlank()) {
        throw IllegalArgumentException("ダウンロード先のアカウントは環境変数にセットしてください。")
    }

    val omoideMemoryFolderId = System.getenv("OMOIDE_FOLDER_ID")
    if (omoideMemoryFolderId.isNullOrBlank()) {
        throw IllegalArgumentException("コンテンツがはいったフォルダの ID を指定してください。")
    }

    val commandName = System.getenv(APPLICATION_RUNNER_KEY)
    if (commandName.isNullOrBlank()) {
        throw IllegalArgumentException("ApplicationRunner の名前を指定してください。")
    }

    SpringApplicationBuilder(OmoideMemoryDownloader::class.java)
        .web(WebApplicationType.NONE) // 🚀 ここで Web を無効化
        .bannerMode(Banner.Mode.OFF) // ついでにバナーも消すとバッチっぽい
        .run(*args)
}
