package com.kasakaid.omoidememory.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kasakaid.omoidememory.data.OmoideUploadPrefsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FCM デバイストークンの更新を受け取るサービス。
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
        // ダウンロード完了通知はシステムの通知トレイに自動表示されるため、
        // data payload を受け取る必要がある場合のみここで処理する。
        // 現状は notification payload のみ使用しているため何もしない。
        Log.d("FCM", "メッセージを受信しました: ${remoteMessage.notification?.title}")
    }
}
