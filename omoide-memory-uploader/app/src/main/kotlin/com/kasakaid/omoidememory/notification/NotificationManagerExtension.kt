package com.kasakaid.omoidememory.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.kasakaid.omoidememory.R
import com.kasakaid.omoidememory.ui.MainActivity
import com.kasakaid.omoidememory.ui.indicator.CONTENTS_UPLOADING

/**
 * MainActivity を開く PendingIntent を生成します（追加設定なし）。
 */
fun Context.createMainPendingIntent(requestCode: Int): PendingIntent = createMainPendingIntent(requestCode = requestCode) {}

/**
 * MainActivity を開く PendingIntent を生成します（Intent の追加設定付き）。
 */
fun Context.createMainPendingIntent(
    requestCode: Int,
    intentConfig: Intent.() -> Unit,
): PendingIntent {
    val intent =
        Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            intentConfig()
        }
    return PendingIntent.getActivity(
        applicationContext,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * NotificationChannel を登録・取得します。
 */
fun Context.ensureNotificationChannel(
    channelId: String,
    channelName: String,
    importance: Int,
): NotificationManager {
    val manager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
    val channel = NotificationChannel(channelId, channelName, importance)
    manager.createNotificationChannel(channel)
    return manager
}

/**
 * 共通の Notification オブジェクトを構築します。
 */
fun Context.createNotification(
    channelId: String,
    title: String,
    message: String,
    smallIcon: Int,
    pendingIntent: PendingIntent,
    largeIcon: Bitmap,
    ongoing: Boolean,
    autoCancel: Boolean,
): Notification =
    NotificationCompat
        .Builder(applicationContext, channelId)
        .setContentTitle(title)
        .setContentText(message)
        .setSmallIcon(smallIcon)
        .setLargeIcon(largeIcon)
        .setContentIntent(pendingIntent)
        .setOngoing(ongoing)
        .setAutoCancel(autoCancel)
        .build()

/**
 * フォアグラウンドサービス用の通知を構築します。
 */
fun Context.createForegroundNotification(
    pendingIntent: PendingIntent,
    message: String,
    channelId: String,
): Notification =
    NotificationCompat
        .Builder(applicationContext, channelId)
        .setContentTitle(CONTENTS_UPLOADING)
        .setContentText(message)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setAutoCancel(false)
        .build()

/**
 * チャンネル確保、通知構築、表示（notify）を一括で行います（デフォルトのアプリアイコンを使用）。
 */
fun Context.showNotification(
    channelId: String,
    channelName: String,
    importance: Int,
    notificationId: Int,
    title: String,
    message: String,
    smallIcon: Int,
    pendingIntent: PendingIntent,
) {
    val width = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
    val height = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
    val defaultIcon =
        (
            ContextCompat.getDrawable(applicationContext, R.mipmap.ic_launcher)
                ?: packageManager.getApplicationIcon(packageName)
        ).toBitmap(width, height)
    showNotification(
        channelId = channelId,
        channelName = channelName,
        importance = importance,
        notificationId = notificationId,
        title = title,
        message = message,
        smallIcon = smallIcon,
        pendingIntent = pendingIntent,
        largeIcon = defaultIcon,
    )
}

/**
 * チャンネル確保、通知構築、表示（notify）を一括で行います（カスタムラージアイコンを指定）。
 */
fun Context.showNotification(
    channelId: String,
    channelName: String,
    importance: Int,
    notificationId: Int,
    title: String,
    message: String,
    smallIcon: Int,
    pendingIntent: PendingIntent,
    largeIcon: Bitmap,
) {
    val manager =
        ensureNotificationChannel(
            channelId = channelId,
            channelName = channelName,
            importance = importance,
        )
    val notification =
        createNotification(
            channelId = channelId,
            title = title,
            message = message,
            smallIcon = smallIcon,
            pendingIntent = pendingIntent,
            largeIcon = largeIcon,
            ongoing = false,
            autoCancel = true,
        )
    manager.notify(notificationId, notification)
}
