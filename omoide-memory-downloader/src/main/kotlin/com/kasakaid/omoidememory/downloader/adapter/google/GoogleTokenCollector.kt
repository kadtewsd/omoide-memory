package com.kasakaid.omoidememory.downloader.adapter.google

import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.UnknownHostException
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * Google のトークンを取得するためのコンポーネント
 * このコンポーネントを介して Google へアクセスする
 */
object GoogleTokenCollector {
    init {
        // DNSの名前解決失敗（Negative Cache）を長時間保持しないように設定
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0")
    }

    private val userCredentials: UserCredentials =
        run {

            val clientId =
                System.getenv("GDRIVE_CLIENT_ID")
                    ?: throw IllegalArgumentException("環境変数 GDRIVE_CLIENT_ID が設定されていません。")
            val clientSecret =
                System.getenv("GDRIVE_CLIENT_SECRET")
                    ?: throw IllegalArgumentException("環境変数 GDRIVE_CLIENT_SECRET が設定されていません。")
            val refreshToken =
                System.getenv("GDRIVE_REFRESH_TOKEN")
                    ?: throw IllegalArgumentException("環境変数 GDRIVE_REFRESH_TOKEN が設定されていません。")

            UserCredentials
                .newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build()
        }

    fun refreshIfNeeded() {
        try {
            userCredentials.refresh()
        } catch (e: UnknownHostException) {
            logger.error { "Google 認証サーバへの接続に失敗しました (DNSエラー)。ネットワーク接続を確認してください: ${e.message}" }
            // 名前解決できない場合は続行不能のため、強制アボート
            exitProcess(1)
        } catch (e: Exception) {
            logger.error(e) { "トークンのリフレッシュに失敗しました。" }
            throw e
        }
    }

    fun asHttpCredentialsAdapter(): HttpCredentialsAdapter = HttpCredentialsAdapter(userCredentials)
}
