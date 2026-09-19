package com.kasakaid.omoidememory.commentimport.domain.model

import com.kasakaid.omoidememory.domain.Extension
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import java.util.UUID

typealias FileName = String

/**
 * コメントのファイルの行
 */
class FileLine(
    val line: String,
) {
    val parsedLines: List<String> =
        run {
            val result = mutableListOf<String>()
            val current = java.lang.StringBuilder()
            var inQuotes = false
            for (char in line) {
                when (char) {
                    '"' -> {
                        // ダブルクォーテーションにはいった
                        inQuotes = !inQuotes
                    }

                    ',' if !inQuotes -> {
                        result.add(current.toString())
                        current.clear()
                    }

                    else -> {
                        current.append(char)
                    }
                }
            }
            result.add(current.toString())
            result
        }

    fun isValidSize(): Boolean = parsedLines.size >= 3

    val fileName by lazy { parsedLines.firstOrNull()?.trim() ?: "" }
    val mediaId: UUID = MyUUIDGenerator.generateUUIDv7()
    val mediaType by lazy { Extension.of(fileName).mimeType }

    val authorDate by lazy { parsedLines.last().trim() }
    val authorParts by lazy { authorDate.split(Regex("[·・]"), limit = 2) }
    val commentBody by lazy { parsedLines.subList(1, parsedLines.size - 1).joinToString(",").trim() }
    val commenterName by lazy { if (authorParts.isNotEmpty()) authorParts[0].trim() else "" }
}
