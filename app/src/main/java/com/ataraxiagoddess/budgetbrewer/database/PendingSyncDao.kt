package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.ataraxiagoddess.budgetbrewer.data.PendingSync
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_sync ORDER BY timestamp ASC")
    fun getAllPending(): Flow<List<PendingSync>>

    @Query("SELECT * FROM pending_sync ORDER BY timestamp ASC")
    suspend fun getAllPendingSync(): List<PendingSync>

    @Insert
    suspend fun insert(pending: PendingSync)

    @Delete
    suspend fun delete(pending: PendingSync)

    @Query("DELETE FROM pending_sync WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)

    @Query("DELETE FROM pending_sync")
    suspend fun deleteAll()
}