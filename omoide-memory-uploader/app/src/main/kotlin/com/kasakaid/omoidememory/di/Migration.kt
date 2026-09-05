package com.kasakaid.omoidememory.di

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `upload_reports` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `lastPoint` TEXT NOT NULL,
                    `totalRequestCount` INTEGER NOT NULL,
                    `successCount` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `message` TEXT
                )
                """.trimIndent(),
            )
        }
    }
