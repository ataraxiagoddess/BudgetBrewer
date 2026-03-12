package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ataraxiagoddess.budgetbrewer.data.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId")
    fun getExpensesForCategory(categoryId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE categoryId IN (SELECT id FROM expense_categories WHERE budgetId = :budgetId)")
    fun getExpensesForBudget(budgetId: String): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE categoryId IN (SELECT id FROM expense_categories WHERE budgetId = :budgetId)")
    fun getTotalExpenses(budgetId: String): Flow<Double?>

    // For sync
    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesSync(): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): Expense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}