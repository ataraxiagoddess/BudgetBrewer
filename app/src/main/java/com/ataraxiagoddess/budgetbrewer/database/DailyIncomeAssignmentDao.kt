package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ataraxiagoddess.budgetbrewer.data.DailyIncomeAssignment
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyIncomeAssignmentDao {
    @Query("SELECT * FROM daily_income_assignments WHERE budgetId = :budgetId")
    fun getAssignmentsForBudget(budgetId: String): Flow<List<DailyIncomeAssignment>>

    @Query("SELECT * FROM daily_income_assignments WHERE budgetId = :budgetId AND dayOfMonth = :day")
    fun getAssignmentsForDay(budgetId: String, day: Int): Flow<List<DailyIncomeAssignment>>

    @Query("SELECT incomeId FROM daily_income_assignments WHERE budgetId = :budgetId")
    suspend fun getAssignedIncomeIds(budgetId: String): List<String>   // incomeId is now String

    @Query("SELECT * FROM daily_income_assignments WHERE budgetId = :budgetId AND incomeId = :incomeId LIMIT 1")
    suspend fun getAssignmentByIncomeId(budgetId: String, incomeId: String): DailyIncomeAssignment?

    // For sync
    @Query("SELECT * FROM daily_income_assignments")
    suspend fun getAllAssignmentsSync(): List<DailyIncomeAssignment>

    @Query("SELECT * FROM daily_income_assignments WHERE id = :id")
    suspend fun getAssignmentById(id: String): DailyIncomeAssignment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignment: DailyIncomeAssignment)

    @Delete
    suspend fun delete(assignment: DailyIncomeAssignment)

    @Query("DELETE FROM daily_income_assignments WHERE budgetId = :budgetId AND incomeId = :incomeId")
    suspend fun deleteByIncomeId(budgetId: String, incomeId: String)

    @Query("DELETE FROM daily_income_assignments")
    suspend fun deleteAll()
}