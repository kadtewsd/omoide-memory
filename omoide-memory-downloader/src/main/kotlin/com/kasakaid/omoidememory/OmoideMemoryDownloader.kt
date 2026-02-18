package com.kasakaid.omoidememory

import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder

const val APPLICATION_RUNNER_KEY = "runner_name"

@SpringBootApplication
class OmoideMemoryDownloader

fun main(args: Array<String>) {

    val destination = System.getenv("omoide_backup_destination")

    if (destination.isNullOrBlank()) {
        throw IllegalArgumentException("ダウンロードファイルの保存先を指定してください。")
    }

    val gdriveAccount = System.getenv("omoide_backup_gdrive_account")
    if (gdriveAccount.isNullOrBlank()) {
        throw IllegalArgumentException("ダウンロード先のアカウントは環境変数にセットしてください。")
    }

    val gdrivePassword = System.getenv("omoide_backup_gdrive_password")
    if (gdrivePassword.isNullOrBlank()) {
        throw IllegalArgumentException("ダウンロード先のアカウントのパスワードは環境変数にセットしてください。")
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
