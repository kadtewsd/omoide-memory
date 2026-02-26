package com.kasakaid.omoidememory.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

object WorkerHelper {
    fun Context.createForegroundInfo(channelId: String): ForegroundInfo {
        // Android 8+ は通知チャンネルが必要
        val channel =
            NotificationChannel(
                channelId,
                "Upload",
                NotificationManager.IMPORTANCE_LOW,
            )

        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.createNotificationChannel(channel)

        val notification =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setContentTitle("アップロード中")
                .setContentText("Google Drive に送信しています")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .build()

        return ForegroundInfo(
            1, // notificationId
            notification,
        )
    }
}
