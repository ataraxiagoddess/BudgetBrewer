package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ataraxiagoddess.budgetbrewer.data.MonthSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthSettingsDao {
    @Query("SELECT * FROM month_settings WHERE budgetId = :budgetId")
    fun getSettingsForBudget(budgetId: String): Flow<MonthSettings?>

    // For sync
    @Query("SELECT * FROM month_settings")
    suspend fun getAllMonthSettingsSync(): List<MonthSettings>

    @Query("SELECT * FROM month_settings WHERE id = :id")
    suspend fun getMonthSettingsById(id: String): MonthSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: MonthSettings)

    @Update
    suspend fun update(settings: MonthSettings)

    @Query("DELETE FROM month_settings")
    suspend fun deleteAll()
}