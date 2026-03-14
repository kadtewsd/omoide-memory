package com.kasakaid.omoidememory.os

import android.content.Context
import java.io.File

/**
 * クラッシュレポートの保存・取得・削除を管理する。
 */
object CrashReporter {
    private const val DIRECTORY_NAME = "crashes"

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
        stackTrace: String,
    ) {
        val fileName = "crash_${System.currentTimeMillis()}.json"
        val file = File(getCrashDir(context), fileName)
        val json =
            """
            {
                "timestamp": ${System.currentTimeMillis()},
                "stackTrace": "${stackTrace.replace("\n", "\\n").replace("\"", "\\\"")}"
            }
            """.trimIndent()
        file.writeText(json)
    }
}
