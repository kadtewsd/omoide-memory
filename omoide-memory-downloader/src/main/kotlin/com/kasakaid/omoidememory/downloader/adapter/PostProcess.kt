package com.kasakaid.omoidememory.downloader.adapter

import arrow.core.raise.either
import com.google.api.client.json.gson.GsonFactory
import com.kasakaid.omoidememory.downloader.adapter.google.AccessTokenRetrieveError
import com.kasakaid.omoidememory.downloader.adapter.google.PushNotification
import com.kasakaid.omoidememory.downloader.domain.DriveService
import com.kasakaid.omoidememory.downloader.service.FileIOFinish
import com.kasakaid.omoidememory.r2dbc.transaction.RollbackException
import com.kasakaid.omoidememory.utility.OneLineLogFormatter
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name

private val logger = KotlinLogging.logger {}

object PostProcess {
    private val errorLogFileName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"))
    private val failedPaths = java.util.Collections.synchronizedList(mutableListOf<Path>())
    private var successCount = 0
    private var failureCount = 0

    fun onFailure(failure: DriveService.WriteError): DriveService.WriteError =
        failure.run {
            when (failure) {
                is DriveService.WriteError -> {
                    failure.paths.forEach {
                        failedPaths.add(it)
                        failureCount++
                        logger.error { "バックアップ時になんらかのエラー発生。${it.name}の物理ファイルを削除します。" }
                        Files.deleteIfExists(it)
                    }
                }
            }
            failure
        }

    /**
     * ダウンロード完了の PUSH 通知を送信します（引数は非 Null 必須）。
     *
     * @param pushNotification PUSH 通知構成要素
     * @param projectId GCP プロジェクト ID
     */
    fun sendNotification(
        pushNotification: PushNotification,
        projectId: String,
    ) {
        either {
            val accessToken = pushNotification.accessToken.bind()
            val messageText = "成功 : ${successCount}件、失敗 : ${failureCount}件で完了しました"

            mapOf(
                "token" to pushNotification.deviceToken,
                "data" to
                    mapOf(
                        "title" to "ダウンロード完了",
                        "body" to messageText,
                    ) +
                    pushNotification.pushIcon.fold(
                        ifLeft = { emptyMap() },
                        ifRight = { mapOf("icon_base64" to it) },
                    ),
                "android" to
                    mapOf(
                        "priority" to "high",
                    ),
            ).let { messagePayload ->
                postToFcm(
                    projectId = projectId,
                    accessToken = accessToken,
                    body = GsonFactory.getDefaultInstance().toString(mapOf("message" to messagePayload)),
                )
            }
        }.onLeft { error ->
            logger.warn { "アクセストークンの取得に失敗したため PUSH 通知をスキップします: ${error.e.message}" }
        }
    }

    private fun postToFcm(
        projectId: String,
        accessToken: String,
        body: String,
    ) {
        runCatching {
            val fcmEndpointUrl = URL("https://fcm.googleapis.com/v1/projects/%s/messages:send".format(projectId))
            (fcmEndpointUrl.openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json; UTF-8")
                    doOutput = true
                    outputStream.use { stream: OutputStream ->
                        stream.write(body.toByteArray(Charsets.UTF_8))
                    }
                }.let {
                    logger.warn { "PUSH 通知の送信に失敗しました (HTTP ${it.responseCode})" }
                }
        }.onFailure { e ->
            logger.error(e) { "PUSH 通知の送信中に例外が発生しました" }
        }
    }

    fun onSuccess(filePath: FileIOFinish): FileIOFinish =
        filePath.also {
            when (filePath) {
                is FileIOFinish.Skip -> {
                    logger.debug { " ${filePath.filePath} を ${filePath.reason} のためスキップ。" }
                }

                is FileIOFinish.Success -> {
                    successCount++
                    logger.debug { "${filePath.filePath} を正常にバックアップできました。" }
                }
            }
        }

    fun onUnmanaged(transactionRollback: RollbackException) {
        failureCount++
        logger.error { "予期せぬエラー ${OneLineLogFormatter.format(transactionRollback)}" }
        logger.error { transactionRollback.leftValue }
    }
}
