package com.smartspacecleaner.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ActionRecordEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun actionRecords(): ActionRecordDao
}
