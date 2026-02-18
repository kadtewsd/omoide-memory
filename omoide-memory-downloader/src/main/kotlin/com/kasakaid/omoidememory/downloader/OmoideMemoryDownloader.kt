package com.kasakaid.omoidememory.downloader

import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class OmoideMemoryDownloader

const val APPLICATION_RUNNER_KEY = "omoide_application_runner"

fun main(args: Array<String>) {
    // 必要な環境変数チェック
    val destination = System.getenv("OMOIDE_BACKUP_DESTINATION")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_BACKUP_DESTINATION が設定されていません。ダウンロード先ディレクトリを指定してください。例: G:\\いつきくん")

    val postgresqlUrl = System.getenv("OMOIDE_POSTGRESQL_URL")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_POSTGRESQL_URL が設定されていません。例: r2dbc:postgresql://localhost:5432/omoide_memory?currentSchema=omoide_memory")

    val postgresqlUsername = System.getenv("OMOIDE_POSTGRESQL_USERNAME")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_POSTGRESQL_USERNAME が設定されていません。")

    val postgresqlPassword = System.getenv("OMOIDE_POSTGRESQL_PASSWORD")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_POSTGRESQL_PASSWORD が設定されていません。")

    // Google Drive API認証情報（OAuth2のcredentials.jsonのパス、またはサービスアカウントJSONのパス）
    val gdriveCredentialsPath = System.getenv("OMOIDE_GDRIVE_CREDENTIALS_PATH")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_GDRIVE_CREDENTIALS_PATH が設定されていません。Google Drive API認証情報のパスを指定してください。")

    val runnerName = System.getenv(APPLICATION_RUNNER_KEY)
        ?: throw IllegalArgumentException("環境変数 ${APPLICATION_RUNNER_KEY} が設定されていません。実行するApplicationRunnerの名前を指定してください。例: downloadFromGDrive")

    println("=== おもいでメモリ ダウンローダー 起動 ===")
    println("ダウンロード先: $destination")
    println("DB接続先: $postgresqlUrl")
    println("実行Runner: $runnerName")

    SpringApplicationBuilder(OmoideMemoryDownloader::class.java)
        .web(WebApplicationType.NONE) // Web不要（バッチ実行）
        .bannerMode(Banner.Mode.OFF)
        .run(*args)
}
