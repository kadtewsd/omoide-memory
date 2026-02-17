package com.kasakaid.omoidememory.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OmoideMemoryDao {

    @Query("SELECT * FROM uploaded_memories ORDER BY uploadedAt DESC")
    fun getAllUploadedFiles(): Flow<List<OmoideMemory>>

    @Query("SELECT count(*) FROM uploaded_memories")
    fun getUploadedCount(): Flow<Int>

    // ハッシュのリスト（Set）だけを Flow で取得する（メモリ節約のためハッシュのみ）
    @Query("SELECT hash FROM uploaded_memories")
    fun getAllUploadedHashesAsFlow(): Flow<List<String>>

    // ハッシュのリスト（Set）だけを取得する（メモリ節約のためハッシュのみ）
    @Query("SELECT hash FROM uploaded_memories")
    suspend fun getAllUploadedHashes(): List<String>

    // ファイル名のリスト（Set）だけを取得する（メモリ節約のためハッシュのみ）
    @Query("SELECT name FROM uploaded_memories")
    suspend fun getAllUploadedNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadedFile(omoideMemory: OmoideMemory)

    @Query("SELECT * FROM uploaded_memories WHERE id = :hash LIMIT 1")
    suspend fun getFileByHash(hash: String): OmoideMemory?
}
