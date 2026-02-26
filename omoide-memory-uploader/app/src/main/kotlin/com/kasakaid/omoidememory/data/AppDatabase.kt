package com.kasakaid.omoidememory.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LocalFile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun omoideMemoryDao(): OmoideMemoryDao
}
