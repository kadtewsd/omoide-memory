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
    @Query("SELECT name FROM uploaded_memories")
    suspend fun getAllUploadedNames(): List<String>

    // ファイル名のリスト（Set）だけを取得する（メモリ節約のためハッシュのみ）
    @Query("SELECT name FROM uploaded_memories")
    fun getAllUploadedNamesAsFlow(): Flow<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadedFile(omoideMemory: OmoideMemory)
}
