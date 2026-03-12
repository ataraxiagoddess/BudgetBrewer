package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ataraxiagoddess.budgetbrewer.data.Allocation
import kotlinx.coroutines.flow.Flow

@Dao
interface AllocationDao {
    @Query("SELECT * FROM allocations WHERE budgetId = :budgetId")
    fun getAllocationForBudget(budgetId: String): Flow<Allocation?>

    @Query("SELECT * FROM allocations")
    suspend fun getAllAllocationsSync(): List<Allocation>

    @Query("DELETE FROM allocations")
    suspend fun deleteAll()

    @Query("SELECT * FROM allocations WHERE id = :id")
    suspend fun getAllocationById(id: String): Allocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(allocation: Allocation)

    @Update
    suspend fun update(allocation: Allocation)

    @Delete
    suspend fun delete(allocation: Allocation)
}