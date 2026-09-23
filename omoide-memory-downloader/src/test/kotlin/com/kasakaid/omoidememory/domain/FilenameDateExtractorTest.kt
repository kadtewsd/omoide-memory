package com.kasakaid.omoidememory.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FilenameDateExtractorTest {
    @Test
    @DisplayName("Unix Timestamp で日付がとれる")
    fun `単純なUnixtime スタンプを日付変換できる`() {
        val given = "1730293338649.jpg"
        val result = UnixTimestampExtractor.extract(given)
        assertThat(result.toString()).isEqualTo("2024-10-30T22:02:18.649+09:00")
    }

    @Test
    @DisplayName("連番付きでも Unix Timestamp で日付がとれる")
    fun `連番付きのファイル名でUnixtime スタンプを日付変換できる`() {
        val given = "1730293338649-3.jpg"
        val result = UnixTimestampExtractor.extract(given)
        assertThat(result.toString()).isEqualTo("2024-10-30T22:02:18.649+09:00")
    }
}
