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
                System.getProperty("gdriveClientId")
                    ?: throw IllegalArgumentException("システムプロパティ gdriveClientId が設定されていません。")
            val clientSecret =
                System.getProperty("gdriveClientSecret")
                    ?: throw IllegalArgumentException("システムプロパティ gdriveClientSecret が設定されていません。")
            val refreshToken =
                System.getProperty("gdriveRefreshToken")
                    ?: throw IllegalArgumentException("システムプロパティ gdriveRefreshToken が設定されていません。")

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
