package com.kasakaid.omoidememory.di

import android.content.Context
import androidx.room.Room
import com.kasakaid.omoidememory.data.AppDatabase
import com.kasakaid.omoidememory.data.OmoideMemoryDao
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
            ).fallbackToDestructiveMigration() // ← これを追加！
            .build()

    @Provides
    fun provideOmoideMemoryDao(database: AppDatabase): OmoideMemoryDao = database.omoideMemoryDao()
}
