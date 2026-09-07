package com.kasakaid.omoidememory.downloader.adapter

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

private val logger = KotlinLogging.logger {}

/**
 * FCM HTTP v1 API を使って Android 端末へ PUSH 通知を送信するサービス。
 *
 * ## 依存
 * - FCM HTTP v1 エンドポイント: `https://fcm.googleapis.com/v1/projects/{PROJECT_ID}/messages:send`
 * - 認証: Service Account の access token（google-auth-library-oauth2-http で取得）
 *
 * ## 環境変数
 * - `GOOGLE_SA_CREDENTIAL_PATH`: FCM 送信権限を持つ Service Account の JSON 鍵ファイルパス
 * - `FCM_PROJECT_ID`: Firebase プロジェクト ID
 */
object PushNotificationService {
    /**
     * FCM HTTP v1 API のエンドポイントテンプレート。
     * プロジェクト ID を埋め込んで使用する。
     */
    private const val FCM_ENDPOINT_TEMPLATE = "https://fcm.googleapis.com/v1/projects/%s/messages:send"

    /**
     * FCM アクセストークン取得に必要な OAuth2 スコープ。
     */
    private const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

    /**
     * ダウンロード完了後に端末へ PUSH 通知を送信します。
     *
     * @param deviceToken 送信先の FCM デバイストークン
     * @param successCount ダウンロード成功件数
     * @param failureCount ダウンロード失敗件数
     */
    fun send(
        deviceToken: String,
        successCount: Int,
        failureCount: Int,
    ) {
        val saPath = System.getenv("GOOGLE_SA_CREDENTIAL_PATH")
        if (saPath.isNullOrBlank()) {
            logger.warn { "GOOGLE_SA_CREDENTIAL_PATH が未設定のため PUSH 通知をスキップします" }
            return
        }
        val projectId = System.getenv("FCM_PROJECT_ID")
        if (projectId.isNullOrBlank()) {
            logger.warn { "FCM_PROJECT_ID が未設定のため PUSH 通知をスキップします" }
            return
        }

        val accessToken = fetchAccessToken(saPath = saPath)
        if (accessToken == null) {
            logger.warn { "アクセストークンの取得に失敗したため PUSH 通知をスキップします" }
            return
        }

        val body =
            buildFcmRequestBody(
                deviceToken = deviceToken,
                successCount = successCount,
                failureCount = failureCount,
            )
        postToFcm(
            projectId = projectId,
            accessToken = accessToken,
            body = body,
        )
    }

    /**
     * Service Account JSON 鍵ファイルから FCM 用 OAuth2 アクセストークンを取得します。
     *
     * @return アクセストークン文字列。取得失敗時は null
     */
    private fun fetchAccessToken(saPath: String): String? =
        runCatching {
            val credentials =
                com.google.auth.oauth2.ServiceAccountCredentials
                    .fromStream(java.io.FileInputStream(saPath))
                    .createScoped(listOf(FCM_SCOPE))
            credentials.refreshIfExpired()
            credentials.accessToken.tokenValue
        }.onFailure { e ->
            logger.error(e) { "Service Account からのアクセストークン取得に失敗しました" }
        }.getOrNull()

    /**
     * FCM HTTP v1 API リクエストボディ (JSON) を生成します。
     *
     * @param deviceToken 送信先デバイストークン
     * @param successCount 成功件数
     * @param failureCount 失敗件数
     */
    private fun buildFcmRequestBody(
        deviceToken: String,
        successCount: Int,
        failureCount: Int,
    ): String {
        val messageText = "成功 : ${successCount}件、失敗 : ${failureCount}件で完了しました"
        return """
            {
              "message": {
                "token": "$deviceToken",
                "notification": {
                  "title": "ダウンロード完了",
                  "body": "$messageText"
                }
              }
            }
            """.trimIndent()
    }

    /**
     * FCM HTTP v1 API エンドポイントへ POST リクエストを送信します。
     *
     * @param projectId Firebase プロジェクト ID
     * @param accessToken OAuth2 アクセストークン
     * @param body JSON リクエストボディ文字列
     */
    private fun postToFcm(
        projectId: String,
        accessToken: String,
        body: String,
    ) {
        runCatching {
            val url = URL(FCM_ENDPOINT_TEMPLATE.format(projectId))
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json; UTF-8")
            connection.doOutput = true

            connection.outputStream.use { stream: OutputStream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.info { "PUSH 通知を送信しました (HTTP $responseCode)" }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "(no body)"
                logger.warn { "PUSH 通知の送信に失敗しました (HTTP $responseCode): $errorBody" }
            }
        }.onFailure { e ->
            logger.error(e) { "PUSH 通知の送信中に例外が発生しました" }
        }
    }
}
