package com.smartspacecleaner.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ActionRecordEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun actionRecords(): ActionRecordDao

    companion object {
        /** 1.3: куда перенесён файл при сортировке. Старые записи получают NULL. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE action_records ADD COLUMN movedToPath TEXT")
            }
        }
    }
}
