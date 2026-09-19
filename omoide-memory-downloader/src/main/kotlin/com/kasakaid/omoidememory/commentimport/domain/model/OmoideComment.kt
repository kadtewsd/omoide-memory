package com.kasakaid.omoidememory.commentimport.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kasakaid.omoidememory.domain.Extension
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.String
import kotlin.collections.List
import kotlin.text.iterator

typealias FileName = String

/**
 * CSV の行から生まれるコメントオブジェウト
 * 変換層をあちこちに置くと書く量が増えるのでここにまとめてしまう。
 * ACL を馬鹿正直に守っていると変換が妙に入り始めて合理的にみえない。
 * いきなりマッピングしてプロパティを作ってしまう
 */
class OmoideComment private constructor(
    line: String,
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

    private fun isValidSize(): Boolean = parsedLines.size >= 3

    val fileName by lazy { parsedLines.firstOrNull()?.trim() ?: "" }
    val feedId: UUID = MyUUIDGenerator.generateUUIDv7()
    val mediaType by lazy { Extension.of(fileName).mimeType }

    private val authorDate by lazy { parsedLines.last().trim() }
    private val authorParts by lazy { authorDate.split(Regex("[·・]"), limit = 2) }
    val commentBody by lazy { parsedLines.subList(1, parsedLines.size - 1).joinToString(",").trim() }
    val commenterName by lazy { if (authorParts.isNotEmpty()) authorParts[0].trim() else "" }

    val commentedAt: OffsetDateTime by lazy {
        OmoideCommentedDateFactory
            .create(
                fileName = fileName,
                authorParts = authorParts,
            ).fold(
                ifLeft = {
                    throw IllegalStateException("パース不可能なコメントです: $fileName $line $it")
                },
                ifRight = { it },
            )
    }

    class ParseError(
        val message: String,
    )

    companion object {
        fun parse(line: String): Either<ParseError, OmoideComment> {
            val comment = OmoideComment(line)
            if (!comment.isValidSize()) return ParseError(line).left()
            return try {
                comment.commentedAt
                comment.right()
            } catch (e: IllegalStateException) {
                ParseError(e.message ?: "").left()
            }
        }
    }
}

interface OmoideCommentRepository {
    suspend fun add(omoideComment: OmoideComment)

    suspend fun deleteByFileName(fileName: FileName)
}
