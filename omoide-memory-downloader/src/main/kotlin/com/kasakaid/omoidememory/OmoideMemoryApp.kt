package com.kasakaid.omoidememory

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class OmoideMemoryApp

const val APPLICATION_RUNNER_KEY = "runnerName"

val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    // コマンドはプロパティにセットしてもらう
    val runnerName =
        System.getProperty(APPLICATION_RUNNER_KEY)
            ?: throw IllegalArgumentException("システムプロパティ $APPLICATION_RUNNER_KEY が設定されていません。実行するApplicationRunnerの名前を指定してください。例: downloadFromGDrive")

    logger.debug { "=== おもいでメモリ ダウンローダー 起動 ===" }
    logger.debug { "実行Runner: $runnerName" }

    SpringApplicationBuilder(OmoideMemoryApp::class.java)
        .web(WebApplicationType.NONE) // Web不要（バッチ実行）
        .bannerMode(Banner.Mode.OFF)
        .run(*args)
        .also {
            logger.debug { "DB接続先 (R2DBC): ${it.environment.getProperty("spring.r2dbc.url")}" }
        }
}
