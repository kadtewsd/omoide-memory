package com.kasakaid.omoidememory.downloader.adapter.google

import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.UnknownHostException
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * Google のトークンを取得するためのコンポーネント
 * 複数の認証情報を JSON から読み込む
 */
object GoogleTokenCollector {
    init {
        // DNSの名前解決失敗（Negative Cache）を長時間保持しないように設定
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0")
    }

    @Serializable
    data class GoogleCredential(
        val client_id: String,
        val client_secret: String,
        val refresh_token: String,
    )

    val allCredentials: List<UserCredentials> =
        run {
            val credentialsPath =
                System.getenv("USER_CREDENTIALS_PATH")
                    ?: throw IllegalArgumentException("環境変数 USER_CREDENTIALS_PATH が設定されていません。")

            val jsonText = Path.of(credentialsPath).readText()
            val credentials = Json.decodeFromString<List<GoogleCredential>>(jsonText)

            credentials.map { cred ->
                UserCredentials
                    .newBuilder()
                    .setClientId(cred.client_id)
                    .setClientSecret(cred.client_secret)
                    .setRefreshToken(cred.refresh_token)
                    .build()
            }
        }

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
