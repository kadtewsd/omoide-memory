package com.kasakaid.omoidememory.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.UploadReport
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.data.UploadReportConverters
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.data.UploadReportDao

@Database(
    entities = [
        OmoideMemory::class,
        UploadReport::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(UploadReportConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun omoideMemoryDao(): OmoideMemoryDao

    abstract fun uploadReportDao(): UploadReportDao
}
