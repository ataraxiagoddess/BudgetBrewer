package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ataraxiagoddess.budgetbrewer.data.DailyChecklist
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyChecklistDao {
    @Query("SELECT * FROM daily_checklist WHERE budgetId = :budgetId")
    fun getChecklistForBudget(budgetId: String): Flow<List<DailyChecklist>>

    @Query("SELECT * FROM daily_checklist WHERE budgetId = :budgetId AND dayOfMonth = :day")
    suspend fun getChecklistItem(budgetId: String, day: Int): DailyChecklist?

    // For sync
    @Query("SELECT * FROM daily_checklist")
    suspend fun getAllChecklistSync(): List<DailyChecklist>

    @Query("SELECT * FROM daily_checklist WHERE id = :id")
    suspend fun getChecklistItemById(id: String): DailyChecklist?


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DailyChecklist)

    @Update
    suspend fun update(item: DailyChecklist)

    @Delete
    suspend fun delete(item: DailyChecklist)

    @Query("DELETE FROM daily_checklist")
    suspend fun deleteAll()
}