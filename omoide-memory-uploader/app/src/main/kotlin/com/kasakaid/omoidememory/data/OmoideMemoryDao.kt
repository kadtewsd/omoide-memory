package com.kasakaid.omoidememory.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OmoideMemoryDao {
    @Query("SELECT count(*) FROM uploaded_memories")
    fun getUploadedCount(): Flow<Int>

    // ファイル名のリスト（Set）だけを取得する（メモリ節約のためハッシュのみ）
    @Query("SELECT id FROM uploaded_memories")
    suspend fun getAllUploadedIds(): List<Long>

    // ファイル名のリスト（Set）だけを取得する（メモリ節約のためハッシュのみ）
    @Query("SELECT id FROM uploaded_memories")
    fun getAllUploadedIdsAsFlow(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadedFile(omoideMemory: OmoideMemory)
}
