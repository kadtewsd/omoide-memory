package com.kasakaid.omoidememory.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

object WorkerHelper {
    /**
     * この Worker を「フォアグラウンド実行」にするための情報を作成します。
     *
     * Android では、アプリが画面に表示されていない状態（バックグラウンド）で
     * 長時間の処理を行うと、システムによって停止・延期されることがあります。
     *
     * 特にファイルのアップロードのような時間がかかる処理は、
     * 画面が消えたり、省電力モードに入った場合に中断される可能性があります。
     *
     * フォアグラウンド実行とは：
     *
     * ・処理中であることを通知としてユーザーに表示する
     * ・「重要な処理を実行中」であることをシステムに伝える
     * ・その結果、停止されにくくなる
     *
     * という仕組みです。
     *
     * このメソッドでは次のものを作成しています：
     *
     * 1. 通知チャンネル（Android 8.0 以上で必須）
     * 2. 処理中に表示される通知
     * 3. Worker に通知を紐づけるための ForegroundInfo オブジェクト
     *
     * 返された ForegroundInfo は、doWork() 内で setForeground() に渡され、
     * この Worker をフォアグラウンド実行に昇格させます。
     *
     * これを行わない場合：
     *
     * ・画面オフ時に処理が止まる可能性がある
     * ・長時間処理がシステムにより制限される可能性がある
     *
     * 注意：
     * ・Android 13 以上では通知権限（POST_NOTIFICATIONS）が必要です
     * ・処理中はユーザーに通知が表示され続けます
     *
     * WorkManager は内部で SystemForegroundService を起動しますが、
     * foregroundServiceType="none" 扱いになります。
     *
     * API 34 ではこれが禁止されています。
     * Google Drive アップロードは：
     * ネットワーク通信
     * データ同期
     * バックグラウンド処理
     * なので適切な type は
     *         <service
     *             android:name="androidx.work.impl.foreground.SystemForegroundService"
     *             android:foregroundServiceType="dataSync"
     *             tools:node="merge" />
     *
     */
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

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+
            ForegroundInfo(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            // API 26〜28
            ForegroundInfo(
                1,
                notification,
            )
        }
    }
}
