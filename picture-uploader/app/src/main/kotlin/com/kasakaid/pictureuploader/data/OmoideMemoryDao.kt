package com.kasakaid.pictureuploader.data

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

    @Query("SELECT EXISTS(SELECT 1 FROM uploaded_memories WHERE id = :hash)")
    suspend fun isFileUploaded(hash: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadedFile(omoideMemory: OmoideMemory)

    @Query("SELECT * FROM uploaded_memories WHERE id = :hash LIMIT 1")
    suspend fun getFileByHash(hash: String): OmoideMemory?
}
