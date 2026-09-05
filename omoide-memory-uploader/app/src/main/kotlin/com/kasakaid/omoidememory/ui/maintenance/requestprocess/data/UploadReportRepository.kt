package com.kasakaid.omoidememory.ui.maintenance.requestprocess.data

import com.kasakaid.omoidememory.ui.maintenance.requestprocess.UploadReport
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * アップロードレポートの永続化と取得を仲介するリポジトリ。
 */
@Singleton
class UploadReportRepository
    @Inject
    constructor(
        private val uploadReportDao: UploadReportDao,
    ) {
        /**
         * アップロードセッションのレポートを登録し、採番された ID を反映して返します。
         */
        suspend fun add(report: UploadReport): Long = uploadReportDao.insert(report = report)

        /**
         * アップロードレポートの進行状況を更新します。
         */
        suspend fun update(report: UploadReport) {
            uploadReportDao.update(report = report)
        }

        /**
         * 全てのアップロードレポートを降順 Flow で取得します。
         */
        fun getAllReports(): Flow<List<UploadReport>> = uploadReportDao.getAllAsFlow()

        /**
         * 特定のレポートを監視する Flow を取得します。
         */
        fun getReport(id: Long): Flow<UploadReport?> = uploadReportDao.findByIdAsFlow(id = id)

        suspend fun findById(id: Long): UploadReport? = uploadReportDao.findById(id = id)

        /**
         * 特定のレポートを削除します。
         */
        suspend fun deleteReport(id: Long) {
            uploadReportDao.deleteById(id = id)
        }

        /**
         * 全てのレポートを削除します。
         */
        suspend fun deleteAllReports() {
            uploadReportDao.deleteAll()
        }
    }
