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
    val destination =
        System.getenv("OMOIDE_BACKUP_DESTINATION")
            ?: throw IllegalArgumentException("環境変数 OMOIDE_BACKUP_DESTINATION が設定されていません。ダウンロード先ディレクトリを指定してください。例: G:\\my-memory")
    if (!Path.of(destination).isAbsolute) {
        throw IllegalArgumentException("絶対パスを指定します。")
    }

    // Google Drive API認証情報（OAuth2のcredentials.jsonのパス、またはサービスアカウントJSONのパス）
    val relativePath =
        System.getenv("OMOIDE_GDRIVE_CREDENTIALS_PATH")
            ?: throw IllegalArgumentException(
                """
                環境変数 OMOIDE_GDRIVE_CREDENTIALS_PATH が設定されていません。
                区切り文字を '/' 区切りで指定してください。
                OS が Windows でも同様です。windows のパス区切りが \ 文化ですが Path.of で読み込んで透過的にパスを解決します。
                例: Windows
                  C:/dev/secrets/omoide-memory/omoide-memory-sa.json
                    Mac
                  /Users/user/secrets/omoide-memory/omoide-memory-sa.json
                """.trimIndent(),
            )

    if (!Path.of(relativePath).isAbsolute) {
        throw IllegalArgumentException("絶対パスを指定します。")
    }

    val omoideMemoryFolderId = System.getenv("OMOIDE_FOLDER_ID")
    if (omoideMemoryFolderId.isNullOrBlank()) {
        throw IllegalArgumentException("コンテンツがはいったフォルダの ID を OMOIDE_FOLDER_ID 環境変数に指定してください。")
    }

    // コマンドはプロパティにセットしてもらう
    val runnerName =
        System.getProperty(APPLICATION_RUNNER_KEY)
            ?: throw IllegalArgumentException("システムプロパティ $APPLICATION_RUNNER_KEY が設定されていません。実行するApplicationRunnerの名前を指定してください。例: downloadFromGDrive")

    logger.debug { "=== おもいでメモリ ダウンローダー 起動 ===" }
    logger.debug { "ダウンロード先: $destination" }
    logger.debug { "実行Runner: $runnerName" }

    SpringApplicationBuilder(OmoideMemoryApp::class.java)
        .web(WebApplicationType.NONE) // Web不要（バッチ実行）
        .bannerMode(Banner.Mode.OFF)
        .run(*args)
        .also {
            logger.debug { "DB接続先 (R2DBC): ${it.environment.getProperty("spring.r2dbc.url")}" }
        }
}
