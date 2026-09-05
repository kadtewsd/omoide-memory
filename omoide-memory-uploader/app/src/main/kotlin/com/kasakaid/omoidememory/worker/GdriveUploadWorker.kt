package com.kasakaid.omoidememory.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.data.UploadState
import com.kasakaid.omoidememory.os.CrashReporter
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.UploadReport
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.data.UploadReportRepository
import com.kasakaid.omoidememory.worker.WorkerHelper.createForegroundInfo
import com.kasakaid.omoidememory.worker.WorkerHelper.showUploadErrorNotification
import com.kasakaid.omoidememory.worker.WorkerHelper.withWifiNetworkBinding
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 手動アップロードを試行する
 */
@HiltWorker
class GdriveUploadWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val gdriveUploader: GdriveUploader,
        private val omoideMemoryRepository: OmoideMemoryRepository,
        private val uploadReportRepository: UploadReportRepository,
    ) : CoroutineWorker(appContext, workerParams) {
        companion object {
            const val TAG = "ManualUploadWorker"
        }

        override suspend fun doWork(): Result {
            val targets = omoideMemoryRepository.findBy(state = UploadState.UPLOAD_TRIGGERED)
            val totalCount = targets.size
            var report =
                uploadReportRepository.add(
                    report = UploadReport.initial(totalRequestCount = totalCount),
                )

            if (targets.isEmpty()) {
                Log.d(TAG, "アップロード対象がありません")
                report = report.next(processedCount = 0)
                uploadReportRepository.update(report = report)
                return Result.success()
            }

            // ワーカー起動・処理開始 (Start -> WorkerStarted)
            report = report.next(processedCount = 0)
            uploadReportRepository.update(report = report)

            return try {
                Log.d(TAG, "フォアグラウンド通知設定")
                setForeground(appContext.createForegroundInfo(channelId = "ManualUpload"))
                report = report.next(processedCount = 0)
                uploadReportRepository.update(report = report)

                appContext.withWifiNetworkBinding {
                    report = report.next(processedCount = 0)
                    uploadReportRepository.update(report = report)

                    // 対象件数取得完了ステップへ
                    report = report.next(processedCount = 0)
                    uploadReportRepository.update(report = report)

                    // 🚀 初期進捗 (0 / totalCount) を通知して UI に全体の件数を伝える
                    setProgress(
                        workDataOf(
                            "PROGRESS_CURRENT" to 0,
                            "PROGRESS_TOTAL" to totalCount,
                        ),
                    )
                    var successCount = 0

                    // アップロード中ステップへ
                    report = report.next(processedCount = 0)
                    uploadReportRepository.update(report = report)

                    for ((index, omoideMemory) in targets.withIndex()) {
                        setProgress(
                            workDataOf(
                                "PROGRESS_CURRENT" to index,
                                "PROGRESS_TOTAL" to totalCount,
                            ),
                        )
                        Log.d(TAG, "手動アップロード開始 ${omoideMemory.name}")
                        val result =
                            gdriveUploader.upload(
                                sourceWorker = WorkManagerTag.Manual,
                                pendingFile = omoideMemory,
                            )

                        result
                            .onSuccess { _ ->
                                successResults.add(omoideMemory.done())
                                successCount++
                                Log.i(TAG, "$successCount / $totalCount アップロード試行完了")
                            }.onFailure { error ->
                                val errorMessage = WorkerHelper.getReadableErrorMessage(throwable = error)
                                Log.e(TAG, "アップロード失敗: $errorMessage", error)
                                if (successResults.isNotEmpty()) {
                                    omoideMemoryRepository.upsert(entities = successResults)
                                }
                                val pendingCount = totalCount - successResults.size
                                Log.i(TAG, "処理対象前の UPLOAD_TRIGGERED となっているレコード $pendingCount 件は何もせず次回に回します ")
                                appContext.showUploadErrorNotification(errorMessage = errorMessage)
                                return@withWifiNetworkBinding Result.failure(
                                    workDataOf(
                                        "PENDING_COUNT" to pendingCount,
                                        "ERROR_MESSAGE" to errorMessage,
                                    ),
                                )
                            }
                    }

                    Log.d(TAG, "すべて成功になるので UPLOAD_TRIGGERED のレコードが消える")
                    omoideMemoryRepository.upsert(entities = successResults)
                    // 全件完了ステップへ
                    report = report.next(processedCount = successCount)
                    uploadReportRepository.update(report = report)
                    Result.success(
                        workDataOf(
                            "PENDING_COUNT" to 0,
                        ),
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Worker 実行中に予期せぬエラーが発生しました", t)
                CrashReporter.saveReport(
                    context = appContext,
                    action = "MANUAL_UPLOAD_WORKER",
                    throwable = t,
                )
                throw t
            }
        }
    }
