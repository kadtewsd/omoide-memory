package com.kasakaid.pictureuploader.extension

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun Instant.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(this, ZoneId.systemDefault())