package com.kasakaid.omoidememory.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OmoideMemoryDao {
    @Query("SELECT count(*) FROM uploaded_memories WHERE state = 'DONE'")
    fun getUploadedCount(): Flow<Int>

    // ファイル名のリスト（Set）だけを取得する（メモリ節約のためハッシュのみ）
    @Query("SELECT id FROM uploaded_memories")
    suspend fun getAllUploadedIds(): List<Long>

    @Query("SELECT id FROM uploaded_memories")
    fun getAllUploadedIdsAsFlow(): Flow<Long>

    @Query(
        """
        SELECT * FROM uploaded_memories
        WHERE state = :state
    """,
    )
    suspend fun findReadyForUpload(state: UploadState = UploadState.READY): List<OmoideMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadedFiles(omoideMemories: List<OmoideMemory>)

    @Query("DELETE FROM uploaded_memories WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
