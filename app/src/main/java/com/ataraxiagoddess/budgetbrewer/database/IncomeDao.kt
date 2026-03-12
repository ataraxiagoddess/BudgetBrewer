package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ataraxiagoddess.budgetbrewer.data.Income
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes WHERE budgetId = :budgetId")
    fun getIncomesForBudget(budgetId: String): Flow<List<Income>>

    @Query("SELECT SUM(amount) FROM incomes WHERE budgetId = :budgetId")
    fun getTotalIncome(budgetId: String): Flow<Double?>

    @Query("UPDATE incomes SET currency = :newCurrency")
    suspend fun updateAllCurrency(newCurrency: String)

    // For sync
    @Query("SELECT * FROM incomes")
    suspend fun getAllIncomesSync(): List<Income>

    @Query("SELECT * FROM incomes WHERE id = :id")
    suspend fun getIncomeById(id: String): Income?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(income: Income)

    @Update
    suspend fun update(income: Income)

    @Delete
    suspend fun delete(income: Income)

    @Query("DELETE FROM incomes")
    suspend fun deleteAll()
}