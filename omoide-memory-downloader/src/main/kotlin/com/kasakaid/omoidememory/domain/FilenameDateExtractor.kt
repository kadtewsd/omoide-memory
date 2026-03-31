package com.kasakaid.omoidememory.domain

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.regex.Pattern

object UnixTimestampExtractor {
    fun extract(filename: String): Option<OffsetDateTime> =
        filename.substringBeforeLast('.').toLongOrNull()?.let { timestamp ->
            try {
                OffsetDateTime
                    .ofInstant(
                        Instant.ofEpochMilli(timestamp),
                        ZoneId.systemDefault(),
                    ).some()
            } catch (e: Exception) {
                None
            }.filterModernEra()
        } ?: None
}

object YyyyMmDdExtractor {
    private val pattern = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})")

    fun extract(filename: String): Option<OffsetDateTime> =
        pattern.matcher(filename).takeIf { it.find() }?.let { matcher ->
            try {
                LocalDateTime
                    .of(
                        matcher.group(1).toInt(),
                        matcher.group(2).toInt(),
                        matcher.group(3).toInt(),
                        12,
                        0,
                    ).atZone(ZoneId.systemDefault())
                    .toOffsetDateTime()
                    .some()
            } catch (e: Exception) {
                None
            }.filterModernEra()
        } ?: None
}

object YyyyMmExtractor {
    private val pattern = Pattern.compile("(\\d{4})(\\d{2})")

    fun extract(filename: String): Option<OffsetDateTime> {
        val cleaned = filename.replace("_", "").replace("-", "")
        return pattern.matcher(cleaned).takeIf { it.find() }?.let { matcher ->
            try {
                LocalDateTime
                    .of(
                        matcher.group(1).toInt(),
                        matcher.group(2).toInt(),
                        1,
                        12,
                        0,
                    ).atZone(ZoneId.systemDefault())
                    .toOffsetDateTime()
                    .some()
            } catch (e: Exception) {
                None
            }.filterModernEra()
        } ?: None
    }
}

/**
 * ファイル名から取得日付を類推する
 */
fun extractDateFromFilename(filename: String): Option<OffsetDateTime> =
    YyyyMmDdExtractor
        .extract(filename)
        .onNone { YyyyMmExtractor.extract(filename) }
        .onNone { UnixTimestampExtractor.extract(filename) }

/**
 * 20xx年代以外の日付は不正と見なす
 */
private fun Option<OffsetDateTime>.filterModernEra(): Option<OffsetDateTime> = filter { it.year in 2000..2099 }
