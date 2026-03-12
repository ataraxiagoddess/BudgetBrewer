package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ataraxiagoddess.budgetbrewer.data.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseCategoryDao {
    @Query("SELECT * FROM expense_categories WHERE budgetId = :budgetId ORDER BY displayOrder")
    fun getCategoriesForBudget(budgetId: String): Flow<List<ExpenseCategory>>

    @Query("SELECT * FROM expense_categories WHERE id = :categoryId")
    fun getCategory(categoryId: String): Flow<ExpenseCategory?>

    // For sync
    @Query("SELECT * FROM expense_categories")
    suspend fun getAllCategoriesSync(): List<ExpenseCategory>

    @Query("SELECT * FROM expense_categories WHERE id = :id")
    suspend fun getCategoryById(id: String): ExpenseCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: ExpenseCategory)

    @Update
    suspend fun update(category: ExpenseCategory)

    @Delete
    suspend fun delete(category: ExpenseCategory)

    @Query("DELETE FROM expense_categories WHERE budgetId = :budgetId")
    suspend fun deleteAllForBudget(budgetId: String)

    @Query("DELETE FROM expense_categories")
    suspend fun deleteAll()
}