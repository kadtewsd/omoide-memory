package com.kasakaid.omoidememory.os

import android.content.Context
import java.io.File

/**
 * Exception / Throwable を JSON 埋め込み用およびログ用の 1 行文字列（改行・ダブルクォートを置換）に変換する。
 */
fun Throwable.toOneLine(): String =
    this
        .stackTraceToString()
        .replace("\n", "\\n")
        .replace("\"", "\\\"")

/**
 * クラッシュレポートの保存・取得・削除を管理する。
 */
object CrashReporter {
    private const val DIRECTORY_NAME = "crashes"

    /**
     * クラッシュ発生時にスタックトレースを自動保存するためのハンドラ登録
     */
    fun init(context: Context) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveReport(
                context = context,
                action = "CRASH",
                throwable = throwable,
            )
            // 既存ハンドラ（Crashlytics 等）を壊さないよう必ず移譲する
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun getCrashDir(context: Context): File {
        val dir = File(context.filesDir, DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getReportFiles(context: Context): List<File> = getCrashDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun deleteReport(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    fun deleteAll(context: Context) {
        getCrashDir(context).listFiles()?.forEach { it.delete() }
    }

    // 🚀 手動でレポートを追加するテスト用、または予期せぬエラー時に利用
    fun saveReport(
        context: Context,
        action: String,
        throwable: Throwable,
    ) {
        val fileName = "crash_${System.currentTimeMillis()}.json"
        val file = File(getCrashDir(context), fileName)
        val json =
            """
            {
                "timestamp": ${System.currentTimeMillis()},
                "action": "$action",
                "message" : "${throwable.message}
                "stackTrace": "${throwable.toOneLine()}"
            }
            """.trimIndent()
        file.writeText(json)
    }
}
