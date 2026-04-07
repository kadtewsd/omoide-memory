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
    private val clientId by lazy {
        System.getenv("GDRIVE_CLIENT_ID")
            ?: throw IllegalArgumentException("環境変数 GDRIVE_CLIENT_ID が設定されていません。")
    }
    private val clientSecret by lazy {
        System.getenv("GDRIVE_CLIENT_SECRET")
            ?: throw IllegalArgumentException("環境変数 GDRIVE_CLIENT_SECRET が設定されていません。")
    }

    val refreshTokens: List<RefreshToken> by lazy {
        val tokens =
            System.getenv("GDRIVE_REFRESH_TOKENS")
                ?: throw IllegalArgumentException("環境変数 GDRIVE_REFRESH_TOKENS が設定されていません。")
        tokens.split(",").map { it.trim() }
    }

    init {
        // DNSの名前解決失敗（Negative Cache）を長時間保持しないように設定
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0")
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

    fun asHttpCredentialsAdapter(userCredentials: com.google.auth.oauth2.UserCredentials): HttpCredentialsAdapter =
        HttpCredentialsAdapter(userCredentials)

    /**
     * リフレッシュトークンを使ってアクセストークンを新しく生成します。
     */
    suspend fun <T> executeWithSafeRefresh(
        token: RefreshToken,
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            if (e.statusCode == 401) {
                logger.warn { "401 Unauthorized detected. Refreshing token manually and retrying..." }
                refreshIfNeeded(createUserCredentials(token))
                block()
            } else {
                throw e
            }
        }
}
