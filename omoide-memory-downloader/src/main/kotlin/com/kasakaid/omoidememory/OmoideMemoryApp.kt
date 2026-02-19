package com.kasakaid.omoidememory

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import java.nio.file.Path

@SpringBootApplication
@ConfigurationPropertiesScan
class OmoideMemoryApp

const val APPLICATION_RUNNER_KEY = "runner_name"

val logger = KotlinLogging.logger {}
fun main(args: Array<String>) {
    // 必要な環境変数チェック
    val destination = System.getenv("OMOIDE_BACKUP_DESTINATION")
        ?: throw IllegalArgumentException("環境変数 OMOIDE_BACKUP_DESTINATION が設定されていません。ダウンロード先ディレクトリを指定してください。例: G:\\my-memory")

    // Google Drive API認証情報（OAuth2のcredentials.jsonのパス、またはサービスアカウントJSONのパス）
    val relativePath = System.getenv("OMOIDE_GDRIVE_CREDENTIALS_PATH_FROM_HOME")
        ?: throw IllegalArgumentException(
            """
        環境変数 OMOIDE_GDRIVE_CREDENTIALS_PATH_FROM_HOME が設定されていません。
        この値には「ユーザーのホームディレクトリ配下の相対パス」を '/' 区切りで指定してください。
        OS が Windows でも同様です。windows のパス区切りが \ 文化なのでやむを得ず変なパスの指定のさせ方にしています。
        例:
          dev/secrets/omoide-memory/omoide-memory-sa.json
        
        ※ 先頭に '/' を付けないでください。
        """.trimIndent()
        )

    if (Path.of(relativePath).isAbsolute) {
        throw IllegalArgumentException("絶対パスは指定できません")
    }

    val omoideMemoryFolderId = System.getenv("OMOIDE_FOLDER_ID")
    if (omoideMemoryFolderId.isNullOrBlank()) {
        throw IllegalArgumentException("コンテンツがはいったフォルダの ID を OMOIDE_FOLDER_ID 環境変数に指定してください。")
    }

    // コマンドはプロパティにセットしてもらう
    val runnerName = System.getProperty(APPLICATION_RUNNER_KEY)
        ?: throw IllegalArgumentException("システムプロパティ $APPLICATION_RUNNER_KEY が設定されていません。実行するApplicationRunnerの名前を指定してください。例: downloadFromGDrive")

    logger.debug { "=== おもいでメモリ ダウンローダー 起動 ===" }
    logger.debug { "ダウンロード先: $destination" }
    logger.debug { "実行Runner: $runnerName" }

    SpringApplicationBuilder(OmoideMemoryApp::class.java)
        .web(WebApplicationType.NONE) // Web不要（バッチ実行）
        .bannerMode(Banner.Mode.OFF)
        .run(*args).also {
            logger.debug { "DB接続先 (R2DBC): ${it.environment.getProperty("spring.r2dbc.url")}" }
        }
}
