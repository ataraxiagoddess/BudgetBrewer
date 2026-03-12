package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ataraxiagoddess.budgetbrewer.data.SpendingEntry
import com.ataraxiagoddess.budgetbrewer.ui.home.MonthlySpending
import kotlinx.coroutines.flow.Flow

@Dao
interface SpendingEntryDao {
    @Query("SELECT * FROM spending_entries WHERE budgetId = :budgetId ORDER BY date ASC")
    fun getSpendingEntriesForBudget(budgetId: String): Flow<List<SpendingEntry>>

    // For sync
    @Query("SELECT * FROM spending_entries")
    suspend fun getAllSpendingEntriesSync(): List<SpendingEntry>

    @Query("SELECT * FROM spending_entries WHERE id = :id")
    suspend fun getSpendingEntryById(id: String): SpendingEntry?


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SpendingEntry)

    @Update
    suspend fun update(entry: SpendingEntry)

    @Delete
    suspend fun delete(entry: SpendingEntry)

    @Query("""
        SELECT 
            CAST(strftime('%m', date / 1000, 'unixepoch') AS INTEGER) as month,
            CAST(strftime('%Y', date / 1000, 'unixepoch') AS INTEGER) as year,
            SUM(amount) as amount
        FROM spending_entries
        WHERE budgetId = :budgetId
        GROUP BY year, month
        ORDER BY year DESC, month DESC
    """)
    fun getMonthlySpendingTotals(budgetId: String): Flow<List<MonthlySpending>>

    @Query("DELETE FROM spending_entries")
    suspend fun deleteAll()
}