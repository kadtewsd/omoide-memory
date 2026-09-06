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
import com.kasakaid.omoidememory.extension.GdriveUploadWorkerKeys
import com.kasakaid.omoidememory.os.CrashReporter
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
            val reportId = inputData.getLong(GdriveUploadWorkerKeys.KEY_REPORT_ID, -1L)
            // はいってなかったら間違った使い方だから findById()!! にする
            val first = uploadReportRepository.findById(reportId)!!

            if (targets.isEmpty()) {
                Log.d(TAG, "アップロード対象がありません")
                uploadReportRepository.update(report = first.targetEmpty())
                return Result.success()
            }

            // ワーカー起動・処理開始 (Start -> WorkerStarted)
            val second = first.next()
            uploadReportRepository.update(report = second)

            val successResults = mutableListOf<OmoideMemory>()
            return try {
                Log.d(TAG, "フォアグラウンド通知設定")
                setForeground(appContext.createForegroundInfo(channelId = "ManualUpload"))
                val third = second.next()
                uploadReportRepository.update(report = third)

                appContext.withWifiNetworkBinding {
                    val forth = third.next()
                    uploadReportRepository.update(report = forth)

                    // 対象件数取得完了ステップへ
                    val fifth = forth.next()
                    uploadReportRepository.update(report = fifth)

                    // 🚀 初期進捗 (0 / totalCount) を通知して UI に全体の件数を伝える
                    setProgress(
                        workDataOf(
                            "PROGRESS_CURRENT" to 0,
                            "PROGRESS_TOTAL" to totalCount,
                        ),
                    )
                    var successCount = 0

                    // アップロード中ステップへ
                    val sixth = fifth.next()
                    uploadReportRepository.update(report = sixth)

                    var uploading = sixth
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
                                uploading = uploading.contentUploaded(processedCount = successCount)
                                uploadReportRepository.update(report = uploading)
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
                    uploadReportRepository.update(report = uploading.next())
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
