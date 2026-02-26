package com.kasakaid.omoidememory.downloader.adapter.google

import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials

/**
 * Google のトークンを取得するためのコンポーネント
 * このコンポーネントを介して Google へアクセスする
 */
object GoogleTokenCollector {
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
        userCredentials.refresh()
    }

    fun asHttpCredentialsAdapter(): HttpCredentialsAdapter = HttpCredentialsAdapter(userCredentials)
}
