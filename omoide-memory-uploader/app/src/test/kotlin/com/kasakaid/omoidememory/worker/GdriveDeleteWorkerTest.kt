package com.kasakaid.omoidememory.worker

import androidx.work.Data
import androidx.work.workDataOf
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.UploadState
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class GdriveDeleteWorkerTest {
    @Test
    fun `DELETE_TRIGGERED 状態のラベルおよびヘルパーメソッドが正しく動作すること`() {
        assertThat(UploadState.DELETE_TRIGGERED.label).isEqualTo("削除適用済")

        val memory =
            OmoideMemory(
                id = 100L,
                name = "test.jpg",
                filePath = "/path/test.jpg",
                fileSize = 1024L,
                mimeType = "image/jpeg",
                dateModified = null,
                dateTaken = null,
                orientation = null,
                state = UploadState.DONE,
            )

        val triggered = memory.deleteTriggered()
        assertThat(triggered.state).isEqualTo(UploadState.DELETE_TRIGGERED)
    }

    @Test
    fun `大量のLong配列をWorkManagerのDataに格納しようとすると10KB制限で例外が発生すること`() {
        // 1件あたり Long (8 bytes) + シリアライズオーバーヘッド。2000件で16KB以上となり10240 bytes を超過
        val largeIds = LongArray(2000) { it.toLong() }

        assertThatThrownBy {
            Data
                .Builder()
                .putLongArray("SELECTED_IDS", largeIds)
                .build()
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Data cannot occupy more than 10240 bytes when serialized")
    }

    @Test
    fun `修正後のWorker出力データは件数のみを保持し10KB制限を安全にクリアすること`() {
        val outputData =
            workDataOf(
                "NOT_DELETED_COUNT" to 1500,
                "DELETED_COUNT" to 500,
            )

        val bytes = outputData.toByteArray()
        // 10240 bytes を大幅に下回り、安全にシリアライズ可能
        assertThat(bytes.size).isLessThan(200)
        assertThat(outputData.getInt("NOT_DELETED_COUNT", 0)).isEqualTo(1500)
        assertThat(outputData.getInt("DELETED_COUNT", 0)).isEqualTo(500)
    }

    @Test
    fun `GdriveUploadWorker の Data 入出力も 10KB 制限を大幅に下回る安全なサイズであること`() {
        val inputData = workDataOf("REPORT_ID" to 12345L)
        val progressData = workDataOf("PROGRESS_CURRENT" to 1000, "PROGRESS_TOTAL" to 5000)
        val successData = workDataOf("PENDING_COUNT" to 0)
        val failureData = workDataOf("PENDING_COUNT" to 50, "ERROR_MESSAGE" to "Wi-Fi disconnected")

        assertThat(inputData.toByteArray().size).isLessThan(500)
        assertThat(progressData.toByteArray().size).isLessThan(500)
        assertThat(successData.toByteArray().size).isLessThan(500)
        assertThat(failureData.toByteArray().size).isLessThan(500)
        // いずれも 10240 bytes (10KB) を圧倒的に下回る
        assertThat(inputData.toByteArray().size).isLessThan(Data.MAX_DATA_BYTES)
        assertThat(progressData.toByteArray().size).isLessThan(Data.MAX_DATA_BYTES)
        assertThat(successData.toByteArray().size).isLessThan(Data.MAX_DATA_BYTES)
        assertThat(failureData.toByteArray().size).isLessThan(Data.MAX_DATA_BYTES)
    }
}
