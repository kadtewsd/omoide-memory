package com.kasakaid.omoidememory.ui.maintenance.requestprocess.data

import androidx.room.TypeConverter
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.UploadPoint

/**
 * [UploadPoint] sealed interface を Room に保存・復元するための TypeConverter。
 */
class UploadReportConverters {
    @TypeConverter
    fun fromUploadPoint(point: UploadPoint): String = point.name

    @TypeConverter
    fun toUploadPoint(value: String): UploadPoint = UploadPoint.fromName(name = value)
}
