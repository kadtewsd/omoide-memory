package com.kasakaid.omoidememor.utility

import java.io.PrintWriter
import java.io.StringWriter

object OneLineLogFormatter {
    fun format(e: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        pw.flush()

        // 改行やタブをスペースに置換して 1 行にまとめる
        val singleLineStack = sw.toString()
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\t", " ")
            .trim()

        return "[${e.message}] : $singleLineStack"
    }
}
