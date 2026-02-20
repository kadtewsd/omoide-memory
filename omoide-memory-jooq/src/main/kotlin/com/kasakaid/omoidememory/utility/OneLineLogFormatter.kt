package com.kasakaid.omoidememory.utility

import java.io.PrintWriter
import java.io.StringWriter

object OneLineLogFormatter {
    fun format(e: Throwable): String {
        // 改行やタブをスペースに置換して 1 行にまとめる
        val singleLineStack =
            ExceptionFormatter
                .allStackTrace(e)
                .toString()
                .replace("\r\n", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .trim()

        return "[${e.message}] : $singleLineStack"
    }
}

object ExceptionFormatter {
    fun allStackTrace(e: Throwable): StringWriter {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        pw.flush()
        return sw
    }
}
