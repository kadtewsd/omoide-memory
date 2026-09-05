package com.kasakaid.omoidememory.ui.maintenance.requestprocess.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.UploadReport
import kotlinx.coroutines.flow.Flow

/**
 * アップロードレポートテーブルへのアクセスを提供する DAO。
 */
@Dao
interface UploadReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: UploadReport): Long

    @Update
    suspend fun update(report: UploadReport)

    @Query("SELECT * FROM upload_reports ORDER BY id DESC")
    fun getAllAsFlow(): Flow<List<UploadReport>>

    @Query("SELECT * FROM upload_reports WHERE id = :id")
    suspend fun findById(id: Long): UploadReport?

    @Query("SELECT * FROM upload_reports WHERE id = :id")
    fun findByIdAsFlow(id: Long): Flow<UploadReport?>

    @Query("DELETE FROM upload_reports WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM upload_reports")
    suspend fun deleteAll()
}
