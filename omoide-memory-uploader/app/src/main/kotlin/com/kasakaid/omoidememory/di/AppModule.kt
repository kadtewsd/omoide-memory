package com.kasakaid.omoidememory.di

import android.content.Context
import androidx.room.Room
import com.kasakaid.omoidememory.data.AppDatabase
import com.kasakaid.omoidememory.data.OmoideMemoryDao
import com.kasakaid.omoidememory.ui.maintenance.requestprocess.data.UploadReportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                "picture-uploader-db",
            ).addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideOmoideMemoryDao(database: AppDatabase): OmoideMemoryDao = database.omoideMemoryDao()

    @Provides
    fun provideUploadReportDao(database: AppDatabase): UploadReportDao = database.uploadReportDao()
}
