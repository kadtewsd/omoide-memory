package com.kasakaid.omoidememory.worker

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.ForegroundInfo
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.kasakaid.omoidememory.notification.createForegroundNotification
import com.kasakaid.omoidememory.notification.createMainPendingIntent
import com.kasakaid.omoidememory.notification.ensureNotificationChannel
import com.kasakaid.omoidememory.notification.showNotification
import com.kasakaid.omoidememory.ui.InitialRoute
import com.kasakaid.omoidememory.ui.indicator.CONTENTS_UPLOADING

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
     * アップロード完了時の通知 ID
     */
    private const val NOTIFICATION_ID_UPLOAD_COMPLETE = 3

    /**
     * 削除完了時の通知 ID
     */
    private const val NOTIFICATION_ID_DELETE_COMPLETE = 4

    /**
     * 削除エラー時の通知 ID
     */
    private const val NOTIFICATION_ID_DELETE_ERROR = 5

    /**
     * ダウンロード完了時の通知 ID
     */
    private const val NOTIFICATION_ID_DOWNLOAD_COMPLETE = 6

    /**
     * アップロードエラー通知用チャンネル ID
     */
    const val CHANNEL_ID_ERROR = "upload_error_channel"

    /**
     * 処理完了通知用チャンネル ID
     */
    const val CHANNEL_ID_COMPLETE = "upload_complete_channel"

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
        ensureNotificationChannel(
            channelId = channelId,
            channelName = "Upload",
            importance = NotificationManager.IMPORTANCE_LOW,
        )
        val pendingIntent = createMainPendingIntent(requestCode = 0)
        val notification =
            createForegroundNotification(
                pendingIntent = pendingIntent,
                message = "Google Drive に送信しています",
                channelId = channelId,
            )

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
        val pendingIntent =
            createMainPendingIntent(requestCode = 1) {
                putExtra(EXTRA_ROUTE, InitialRoute.PENDING.route)
                putExtra(EXTRA_MESSAGE, errorMessage)
            }
        showNotification(
            channelId = CHANNEL_ID_ERROR,
            channelName = "Upload Error",
            importance = NotificationManager.IMPORTANCE_HIGH,
            notificationId = NOTIFICATION_ID_ERROR,
            title = "アップロードでエラーが発生しました",
            message = errorMessage,
            smallIcon = android.R.drawable.stat_notify_error,
            pendingIntent = pendingIntent,
        )
    }

    /**
     * アップロード完了時の通知を表示します。
     * タップするとアプリのメイン画面を開く PendingIntent を含みます。
     */
    fun Context.showUploadCompleteNotification(uploadedCount: Int) {
        val pendingIntent = createMainPendingIntent(requestCode = 2)
        showNotification(
            channelId = CHANNEL_ID_COMPLETE,
            channelName = "処理完了通知",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = NOTIFICATION_ID_UPLOAD_COMPLETE,
            title = "Google Drive アップロード完了",
            message = "${uploadedCount}件のファイルのアップロードが完了しました",
            smallIcon = android.R.drawable.stat_sys_upload_done,
            pendingIntent = pendingIntent,
        )
    }

    /**
     * ドライブ削除完了時の通知を表示します。
     * タップするとアプリのメイン画面を開く PendingIntent を含みます。
     */
    fun Context.showDeleteCompleteNotification(
        deletedCount: Int,
        notDeletedCount: Int,
    ) {
        val message =
            if (notDeletedCount > 0) {
                "${deletedCount}件のファイルを削除しました（未ダウンロードのためスキップ: ${notDeletedCount}件）"
            } else {
                "${deletedCount}件のファイルを削除しました"
            }
        val pendingIntent = createMainPendingIntent(requestCode = 3)
        showNotification(
            channelId = CHANNEL_ID_COMPLETE,
            channelName = "処理完了通知",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = NOTIFICATION_ID_DELETE_COMPLETE,
            title = "Google Drive 削除完了",
            message = message,
            smallIcon = android.R.drawable.stat_sys_warning,
            pendingIntent = pendingIntent,
        )
    }

    /**
     * ダウンロード完了時の PUSH 通知を表示します（デフォルトのアプリアイコンを表示）。
     * タップするとアプリのメイン画面を開く PendingIntent を含みます。
     *
     * @param title 通知タイトル
     * @param message 通知メッセージ本文
     */
    fun Context.showDownloadCompleteNotification(
        title: String,
        message: String,
    ) {
        val pendingIntent = createMainPendingIntent(requestCode = 5)
        showNotification(
            channelId = CHANNEL_ID_COMPLETE,
            channelName = "処理完了通知",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = NOTIFICATION_ID_DOWNLOAD_COMPLETE,
            title = title,
            message = message,
            smallIcon = android.R.drawable.stat_sys_download_done,
            pendingIntent = pendingIntent,
        )
    }

    /**
     * ダウンロード完了時の PUSH 通知を表示します（カスタムアイコンを表示）。
     * タップするとアプリのメイン画面を開く PendingIntent を含みます。
     *
     * @param title 通知タイトル
     * @param message 通知メッセージ本文
     * @param customIcon PUSH 通知ペイロードに含まれるカスタムアイコン (Bitmap)
     */
    fun Context.showDownloadCompleteNotification(
        title: String,
        message: String,
        customIcon: Bitmap,
    ) {
        val pendingIntent = createMainPendingIntent(requestCode = 5)
        showNotification(
            channelId = CHANNEL_ID_COMPLETE,
            channelName = "処理完了通知",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notificationId = NOTIFICATION_ID_DOWNLOAD_COMPLETE,
            title = title,
            message = message,
            smallIcon = android.R.drawable.stat_sys_download_done,
            pendingIntent = pendingIntent,
            largeIcon = customIcon,
        )
    }

    /**
     * ドライブ削除エラー時の通知を表示します。
     * タップするとアプリのメイン画面を開く PendingIntent を含みます。
     */
    fun Context.showDeleteErrorNotification(errorMessage: String) {
        val pendingIntent = createMainPendingIntent(requestCode = 4)
        showNotification(
            channelId = CHANNEL_ID_ERROR,
            channelName = "Upload Error",
            importance = NotificationManager.IMPORTANCE_HIGH,
            notificationId = NOTIFICATION_ID_DELETE_ERROR,
            title = "Google Drive 削除でエラーが発生しました",
            message = errorMessage,
            smallIcon = android.R.drawable.stat_notify_error,
            pendingIntent = pendingIntent,
        )
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
