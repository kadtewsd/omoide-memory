package com.kasakaid.pictureuploader.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [OmoideMemory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun omoideMemoryDao(): OmoideMemoryDao
}
