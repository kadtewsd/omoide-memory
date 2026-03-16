package com.kasakaid.omoidememory.commentimport.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object OmoideCommentedDateFactory {
    private val dateFormatterWithYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val dateFormatterWithoutYear = DateTimeFormatter.ofPattern("MMM d", Locale.US)

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
                    // ファイル名から 20xx 年を探す (PXL_2025~ や 2025~ などに対応)
                    val yearRegex = Regex("(20\\d{2})")
                    val inferredYear =
                        yearRegex
                            .find(fileName)
                            ?.groupValues
                            ?.get(1)
                            ?.toInt()
                            ?: LocalDate.now().year // 推測できない場合は今年

                    monthDay.atYear(inferredYear)
                }

            localDate
                .atTime(LocalTime.MIDNIGHT)
                .atZone(ZoneId.systemDefault())
                .toOffsetDateTime()
                .right()
        } catch (e: Exception) {
            e.left()
        }
    }
}
