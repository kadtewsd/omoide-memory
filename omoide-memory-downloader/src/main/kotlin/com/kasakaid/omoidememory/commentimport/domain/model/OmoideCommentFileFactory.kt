package com.kasakaid.omoidememory.commentimport.domain.model

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.right
import com.kasakaid.omoidememory.domain.Extension
import com.kasakaid.omoidememory.domain.extractDateFromFilename
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * CSV の行から OmoideCommentFile を組み立てるファクトリ。
 * ACL を律儀に守って変換層をあちこちに置くと書く量が増えるので、
 * CSV の行 → ドメインオブジェクトのプロパティへのマッピングはここに集約する。
 *
 * 日付の解決ロジック（旧 OmoideCommentedDateFactory）も
 * ここでしか使われていないため、このファクトリの責務として取り込んでいる。
 */
object OmoideCommentFileFactory {
    class ParseError(
        val message: String,
    )

    private val dateFormatterWithYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val dateFormatterWithoutYear = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val jst = ZoneId.of("Asia/Tokyo")

    fun create(line: String): Either<ParseError, OmoideCommentFile> {
        val parsedLines = parseCsvLine(line)
        if (parsedLines.size < 3) return ParseError(line).left()

        val fileName = parsedLines.first().trim()
        val commenterAndDate = parsedLines.last().trim()
        val authorParts = commenterAndDate.split(Regex("[·・]"), limit = 2)
        val commenterName = authorParts.firstOrNull()?.trim() ?: ""
        val commentBody = parsedLines.subList(1, parsedLines.size - 1).joinToString(",").trim()

        val commentedAt =
            resolveCommentedAt(fileName = fileName, authorParts = authorParts).fold(
                ifLeft = {
                    return ParseError("パース不可能なコメントです: $fileName $line $it").left()
                },
                ifRight = { it },
            )

        return OmoideCommentFile(
            parsedLines = parsedLines,
            omoideComment =
                OmoideComment(
                    feedId = MyUUIDGenerator.generateUUIDv7(),
                    fileName = fileName,
                    mediaType = Extension.of(fileName).mimeType,
                    commentBody = commentBody,
                    commenterName = commenterName,
                    commentedAt = commentedAt,
                ),
        ).right()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> {
                    // ダブルクォーテーションに入った
                    inQuotes = !inQuotes
                }

                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }

                else -> {
                    current.append(char)
                }
            }
        }
        result.add(current.toString())
        return result
    }

    private fun resolveCommentedAt(
        fileName: String,
        authorParts: List<String>,
    ): Either<Throwable, OffsetDateTime> {
        val dateString = if (authorParts.size == 2) authorParts[1].trim() else return Exception("パーツがおかしい").left()
        return try {
            val localDate =
                try {
                    // MMM d, yyyy パターン
                    LocalDate.parse(dateString, dateFormatterWithYear)
                } catch (e: Exception) {
                    // 年がない MMM d パターン
                    val monthDay = MonthDay.parse(dateString, dateFormatterWithoutYear)
                    // ファイル名から 20xx 年と月を探す
                    val fileMonth: OffsetDateTime =
                        extractDateFromFilename(fileName).fold(
                            ifEmpty = {
                                return ZonedDateTime
                                    .of(
                                        LocalDateTime.of(
                                            LocalDate.of(LocalDate.now().year, monthDay.monthValue, monthDay.dayOfMonth),
                                            LocalTime.MIDNIGHT,
                                        ),
                                        jst,
                                    ).toOffsetDateTime()
                                    .right()
                            },
                            ifSome = { it },
                        )
                    val inferredDate: LocalDate = LocalDate.of(fileMonth.year, fileMonth.monthValue, 1)

                    if (monthDay.monthValue < fileMonth.toLocalDate().monthValue) {
                        // 年またぎ（翌年のコメント）として補正する
                        // 撮影月の翌月（expectedMonth）を基準に CSV の月と3ヶ月以上乖離している場合は
                        // 月名の誤認識と判断して expectedMonth を採用する
                        val expectedMonth = (fileMonth.monthValue % 12) + 1
                        val resolvedMonth = if (abs(monthDay.monthValue - expectedMonth) > 3) expectedMonth else monthDay.monthValue
                        LocalDate.of(inferredDate.year + 1, resolvedMonth, monthDay.dayOfMonth)
                    } else {
                        monthDay.atYear(inferredDate.year)
                    }
                }

            localDate
                .atTime(LocalTime.MIDNIGHT)
                .atZone(jst)
                .toOffsetDateTime()
                .right()
        } catch (e: Exception) {
            e.left()
        }
    }
}
