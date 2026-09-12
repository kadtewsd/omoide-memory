package com.kasakaid.omoidememory.worker

import androidx.work.WorkInfo
import com.kasakaid.omoidememory.extension.isActive
import com.kasakaid.omoidememory.extension.progress
import com.kasakaid.omoidememory.extension.requestType
import com.kasakaid.omoidememory.ui.indicator.Progress
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class GoogleDriveRequestTypeTest {
    @Test
    fun `Uploading のプロパティと onCancel が正常に機能すること`() {
        var cancelled = false
        val progress = Progress(progressed = 3, total = 10)
        val upload =
            GoogleDriveRequestType.Uploading(
                requestProgress = progress,
                onCancelAction = { cancelled = true },
            )
        val request: GoogleDriveRequestType = upload

        assertThat(upload.label).isEqualTo("アップロード中")
        assertThat(upload.workerName).isEqualTo("manual_upload")
        assertThat(upload.requestProgress).isEqualTo(progress)
        assertThat(request is GoogleDriveRequestType.Processing).isTrue()

        // パターンマッチの検証（Processing としてまとめて処理可能）
        val matchedLabel =
            when (request) {
                is GoogleDriveRequestType.Processing -> "processing: ${request.label}"
                is GoogleDriveRequestType.None -> "none"
            }
        assertThat(matchedLabel).isEqualTo("processing: アップロード中")

        upload.onCancel()
        assertThat(cancelled).isTrue()
    }

    @Test
    fun `Deleting のプロパティと onCancel が正常に機能すること`() {
        var cancelled = false
        val progress = Progress(progressed = 1, total = 5)
        val delete =
            GoogleDriveRequestType.Deleting(
                requestProgress = progress,
                onCancelAction = { cancelled = true },
            )
        val request: GoogleDriveRequestType = delete

        assertThat(delete.label).isEqualTo("削除中")
        assertThat(delete.workerName).isEqualTo("manual_delete")
        assertThat(delete.requestProgress).isEqualTo(progress)
        assertThat(request is GoogleDriveRequestType.Processing).isTrue()

        // パターンマッチの検証（Processing としてまとめて処理可能）
        val matchedLabel =
            when (request) {
                is GoogleDriveRequestType.Processing -> "processing: ${request.label}"
                is GoogleDriveRequestType.None -> "none"
            }
        assertThat(matchedLabel).isEqualTo("processing: 削除中")

        delete.onCancel()
        assertThat(cancelled).isTrue()
    }

    @Test
    fun `None は Processing ではなく、空の実装を持たないこと`() {
        val request: GoogleDriveRequestType = GoogleDriveRequestType.None

        assertThat(request is GoogleDriveRequestType.Processing).isFalse()

        // パターンマッチの検証（Processing と None を分岐）
        val matchedLabel =
            when (request) {
                is GoogleDriveRequestType.Processing -> "processing: ${request.label}"
                is GoogleDriveRequestType.None -> "none"
            }
        assertThat(matchedLabel).isEqualTo("none")
    }

    @Test
    fun `WorkInfo State isActive が RUNNING と ENQUEUED で true を返すこと`() {
        assertThat(WorkInfo.State.RUNNING.isActive()).isTrue()
        assertThat(WorkInfo.State.ENQUEUED.isActive()).isTrue()
        assertThat(WorkInfo.State.SUCCEEDED.isActive()).isFalse()
        assertThat(WorkInfo.State.FAILED.isActive()).isFalse()
        assertThat(WorkInfo.State.CANCELLED.isActive()).isFalse()
        assertThat(WorkInfo.State.BLOCKED.isActive()).isFalse()
    }

    @Test
    fun `空の WorkInfo リストに対して progress と requestType が null を返すこと`() {
        val emptyList = emptyList<WorkInfo>()
        assertThat(emptyList.progress()).isNull()
        assertThat(emptyList.requestType { p -> GoogleDriveRequestType.Uploading(p) {} }).isNull()
    }

    @Test
    fun `Uploading と Deleting のデフォルトインスタンスが正しい workerName と label を持つこと`() {
        val uploading = GoogleDriveRequestType.Uploading()
        assertThat(uploading.workerName).isEqualTo("manual_upload")
        assertThat(uploading.label).isEqualTo("アップロード中")

        val deleting = GoogleDriveRequestType.Deleting()
        assertThat(deleting.workerName).isEqualTo("manual_delete")
        assertThat(deleting.label).isEqualTo("削除中")
    }
}
