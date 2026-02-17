package com.kasakaid.omoidememory.extension

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun Instant.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(this, ZoneId.systemDefault())


fun Long.toZonedInstant(): Instant {
    // 1. 返ってきた UTCミリ秒を LocalDate (日付のみ) に変換
    // これで「ユーザーが選んだカレンダー上の日付」が確定する
    val localDate = Instant.ofEpochMilli(this)
        .atZone(ZoneId.of("UTC")) // DatePicker は UTC で日付を出すのでここまでは UTC
        .toLocalDate()

    // 2. その日付の「システムデフォルト時間での開始時間 (00:00)」を Instant にする
    // これで JST 00:00:00 の Instant が手に入る
    return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
}