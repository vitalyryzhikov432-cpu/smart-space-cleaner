package com.smartspacecleaner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ActionRecordDao {
    @Insert
    fun insert(entity: ActionRecordEntity): Long

    @Update
    fun update(entity: ActionRecordEntity)

    @Query("SELECT * FROM action_records WHERE id = :id")
    fun get(id: Long): ActionRecordEntity?

    @Query("SELECT * FROM action_records WHERE status = :status ORDER BY id")
    fun byStatus(status: String): List<ActionRecordEntity>

    @Query("SELECT * FROM action_records ORDER BY id")
    fun all(): List<ActionRecordEntity>
}
