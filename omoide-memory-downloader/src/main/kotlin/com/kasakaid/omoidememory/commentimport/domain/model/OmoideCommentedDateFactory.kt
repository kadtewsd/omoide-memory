package com.kasakaid.omoidememory.commentimport.domain.model

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.right
import com.kasakaid.omoidememory.domain.extractDateFromFilename
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

object OmoideCommentedDateFactory {
    private val dateFormatterWithYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val dateFormatterWithoutYear = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val jst = ZoneId.of("Asia/Tokyo")

    fun create(
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

                    if (monthDay.monthValue < (fileMonth.toLocalDate().monthValue)) {
                        // 年またぎ（翌年のコメント）として補正する
                        // さらに、撮影月の翌月（expectedMonth）を基準に CSV の月と3ヶ月以上乖離している場合は
                        // 月名の誤認識（例: "Jun"=6 が実際は "Jan"=1）と判断して expectedMonth を採用する
                        // 例: 撮影=2024年12月, コメント="Jun 15" → expectedMonth=1, diff=5 → January 15, 2025
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
