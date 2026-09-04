package com.kasakaid.omoidememory.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.kasakaid.omoidememory.ui.CONTENTS_UPLOADING
import com.kasakaid.omoidememory.ui.InitialRoute
import com.kasakaid.omoidememory.ui.MainActivity

object WorkerHelper {
    /**
     * フォアグラウンド実行時の通知 ID
     */
    private const val NOTIFICATION_ID_FOREGROUND = 1

    /**
     * アップロードエラー時の通知 ID
     */
    private const val NOTIFICATION_ID_ERROR = 2

    /**
     * アップロードエラー通知用チャンネル ID
     */
    const val CHANNEL_ID_ERROR = "upload_error_channel"

    /**
     * アップロードエラー通知クリック時の遷移先ルート Extra キー
     */
    const val EXTRA_ROUTE = "EXTRA_ROUTE"

    /**
     * アップロードエラー通知クリック時のエラーメッセージ Extra キー
     */
    const val EXTRA_MESSAGE = "EXTRA_MESSAGE"

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
     * 2. 処理中に表示される通知（タップでアプリを前面化する PendingIntent を付与）
     * 3. Worker に通知を紐づけるための ForegroundInfo オブジェクト
     *
     * 返された ForegroundInfo は、doWork() 内で setForeground() に渡され、
     * この Worker をフォアグラウンド実行に昇格させます。
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

        val intent =
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setContentTitle(CONTENTS_UPLOADING)
                .setContentText("Google Drive に送信しています")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+
            ForegroundInfo(
                NOTIFICATION_ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            // API 26〜28
            ForegroundInfo(
                NOTIFICATION_ID_FOREGROUND,
                notification,
            )
        }
    }

    /**
     * アップロードエラー時の通知を表示します。
     * タップするとアプリの「アップロード再開」画面を開く PendingIntent を含みます。
     */
    fun Context.showUploadErrorNotification(errorMessage: String) {
        val channel =
            NotificationChannel(
                CHANNEL_ID_ERROR,
                "Upload Error",
                NotificationManager.IMPORTANCE_HIGH,
            )
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.createNotificationChannel(channel)

        val intent =
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ROUTE, InitialRoute.PENDING.route)
                putExtra(EXTRA_MESSAGE, errorMessage)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID_ERROR)
                .setContentTitle("アップロードでエラーが発生しました")
                .setContentText(errorMessage)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        manager.notify(NOTIFICATION_ID_ERROR, notification)
    }

    /**
     * Wi-Fi ネットワークへのプロセスバインドを行い、ブロックを実行した後に解除します。
     * アップロードループの外部で実行することで、ファイルごとの頻繁なソケット再接続を防ぎ、
     * HTTP コネクションプール (Keep-Alive) を有効活用して SocketTimeoutException を防止します。
     */
    inline fun <T> Context.withWifiNetworkBinding(block: () -> T): T {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)

        return if (activeNetwork != null && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            cm.bindProcessToNetwork(activeNetwork)
            try {
                block()
            } finally {
                cm.bindProcessToNetwork(null)
            }
        } else {
            block()
        }
    }

    /**
     * 例外からユーザー向けの分かりやすいエラーメッセージを生成します。
     */
    fun getReadableErrorMessage(throwable: Throwable): String =
        when {
            throwable is java.net.SocketTimeoutException -> {
                "通信がタイムアウトしました。電波の良い場所で再度お試しください。"
            }

            throwable is GoogleJsonResponseException && throwable.statusCode == 429 -> {
                "Google Drive のリクエスト上限に達しました。しばらく待ってから再度お試しください。"
            }

            throwable is GoogleJsonResponseException && throwable.statusCode == 507 -> {
                "Google Drive のストレージ容量がいっぱいです。"
            }

            throwable is GoogleJsonResponseException && (throwable.statusCode == 401 || throwable.statusCode == 403) -> {
                "Google アカウントの認証エラーが発生しました。再度ログインしてください。"
            }

            throwable is IllegalStateException && throwable.message?.contains("Wi-Fi") == true -> {
                "Wi-Fi に接続されていないためアップロードを中断しました。"
            }

            else -> {
                throwable.message ?: "アップロード中にエラーが発生しました。"
            }
        }
}
