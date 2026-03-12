package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ataraxiagoddess.budgetbrewer.data.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year")
    fun getBudget(month: Int, year: Int): Flow<Budget?>

    @Query("SELECT * FROM budgets ORDER BY year DESC, month DESC")
    fun getAllBudgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgetsSync(): List<Budget>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getBudgetById(id: String): Budget?

    @Query("SELECT * FROM budgets WHERE (year < :year OR (year = :year AND month < :month)) ORDER BY year DESC, month DESC LIMIT 1")
    suspend fun findPreviousBudget(month: Int, year: Int): Budget?

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(budget: Budget): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)  // Add this line
    suspend fun insertOrIgnore(budget: Budget): Long  // Add this method

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)
}