package com.kasakaid.omoidememory.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OmoideMemoryDao {
    @Query("SELECT count(*) FROM uploaded_memories WHERE state IN (:states)")
    fun getUploadedCount(states: List<UploadState>): Flow<Int>

    // ファイル名のリスト（Set）だけを取得する（メモリ節約のためハッシュのみ）
    @Query("SELECT id FROM uploaded_memories")
    suspend fun getAllUploadedIds(): List<Long>

    @Query("SELECT count(id) FROM uploaded_memories")
    fun getAllUploadedIdsAsFlow(): Flow<Long>

    @Query("SELECT * FROM uploaded_memories ORDER BY id DESC")
    suspend fun getAll(): List<OmoideMemory>

    @Query(
        """
        SELECT * FROM uploaded_memories
        WHERE state = :state
    """,
    )
    suspend fun findBy(state: UploadState): List<OmoideMemory>

    @Query("SELECT * FROM uploaded_memories WHERE state = :state")
    fun findByAsFlow(state: UploadState): Flow<List<OmoideMemory>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUploadedFiles(omoideMemories: List<OmoideMemory>)

    @Query("UPDATE uploaded_memories SET state = :state WHERE id IN (:ids)")
    suspend fun update(
        ids: List<Long>,
        state: UploadState,
    )

    @Query("DELETE FROM uploaded_memories WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
