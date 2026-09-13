package com.kasakaid.omoidememory.fcm

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import com.kasakaid.omoidememory.worker.WorkerHelper.showDownloadCompleteNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FCM デバイストークンの更新およびダウンロード完了通知を受け取るサービス。
 *
 * ## トークン更新のタイミング
 * - アプリの初回インストール時
 * - アプリのデータがクリアされたとき
 * - FCM が内部的にトークンをローテーションしたとき（セキュリティ目的）
 *
 * 新しいトークンは SharedPreferences に保存し、次回アップロード時に Drive へ書き込む。
 */
@AndroidEntryPoint
class OmoideFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var prefsRepository: OmoideUploadPrefsRepository

    override fun onNewToken(token: String) {
        Log.d("FCM", "FCM トークンが更新されました")
        prefsRepository.saveDeviceToken(token = token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "ダウンロード完了"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body
        Log.d("FCM", "メッセージを受信しました: $title")
        if (body == null) return

        val iconBase64 = remoteMessage.data["icon_base64"]
        val customIcon =
            iconBase64?.let {
                runCatching {
                    val bytes = Base64.decode(it, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.onFailure { e ->
                    Log.w("FCM", "アイコン画像のデコードに失敗しました", e)
                }.getOrNull()
            }

        if (customIcon != null) {
            showDownloadCompleteNotification(
                title = title,
                message = body,
                customIcon = customIcon,
            )
        } else {
            showDownloadCompleteNotification(
                title = title,
                message = body,
            )
        }
    }
}
