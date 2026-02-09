package com.kasakaid.pictureuploader.di

import android.content.Context
import androidx.room.Room
import com.kasakaid.pictureuploader.data.AppDatabase
import com.kasakaid.pictureuploader.data.OmoideMemoryDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "picture-uploader-db"
        ).build()
    }

    @Provides
    fun provideOmoideMemoryDao(database: AppDatabase): OmoideMemoryDao {
        return database.omoideMemoryDao()
    }
}
