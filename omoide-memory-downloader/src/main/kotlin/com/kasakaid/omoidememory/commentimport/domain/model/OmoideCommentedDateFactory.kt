package com.kasakaid.omoidememory.commentimport.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object OmoideCommentedDateFactory {
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    fun create(authorParts: List<String>): Either<Throwable, OffsetDateTime> {
        val dateString = if (authorParts.size == 2) authorParts[1].trim() else return Exception("パーツがおかしい").left()
        return try {
            LocalDate
                .parse(dateString, dateFormatter)
                .atTime(LocalTime.MIDNIGHT)
                .atZone(ZoneId.systemDefault())
                .toOffsetDateTime()
                .right()
        } catch (e: Exception) {
            e.left()
        }
    }
}
