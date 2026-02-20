package com.kasakaid.omoidememory.utility

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jsonMapper

object JsonMapper {
    val mapper: ObjectMapper =
        jsonMapper {
            addModule(
                KotlinModule
                    .Builder()
                    .build(),
            )
            addModule(JavaTimeModule())

            // LocalDate を timestamp にしない
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            // 不明フィールドは無視
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

            // null を厳密に扱う（Kotlin向け）
            enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        }
}
