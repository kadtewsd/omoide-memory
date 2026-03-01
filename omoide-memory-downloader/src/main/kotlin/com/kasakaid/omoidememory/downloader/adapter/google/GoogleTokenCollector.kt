package com.kasakaid.omoidememory.downloader.adapter.google

import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.UnknownHostException
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

typealias RefreshToken = String

/**
 * Google のトークンを取得するためのコンポーネント
 * 複数の認証情報を JSON から読み込む
 */
object GoogleTokenCollector {
    private val clientId =
        System.getenv("GDRIVE_CLIENT_ID")
            ?: throw IllegalArgumentException("環境変数 GDRIVE_CLIENT_ID が設定されていません。")
    private val clientSecret =
        System.getenv("GDRIVE_CLIENT_SECRET")
            ?: throw IllegalArgumentException("環境変数 GDRIVE_CLIENT_SECRET が設定されていません。")

    init {
        // DNSの名前解決失敗（Negative Cache）を長時間保持しないように設定
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0")
    }

    val refreshTokens: List<RefreshToken> =
        run {
            val tokens =
                System.getenv("GDRIVE_REFRESH_TOKENS")
                    ?: throw IllegalArgumentException("環境変数 GDRIVE_REFRESH_TOKENS が設定されていません。")
            tokens.split(",").map { it.trim() }
        }

    fun createUserCredentials(token: RefreshToken): UserCredentials =
        UserCredentials
            .newBuilder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRefreshToken(token)
            .build()

    fun refreshIfNeeded(userCredentials: UserCredentials) {
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

    fun asHttpCredentialsAdapter(userCredentials: UserCredentials): HttpCredentialsAdapter = HttpCredentialsAdapter(userCredentials)
}
