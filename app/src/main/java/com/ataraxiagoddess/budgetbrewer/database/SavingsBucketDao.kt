/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucketType
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsBucketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bucket: SavingsBucket)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: SavingsTransaction)

    @Update
    suspend fun update(bucket: SavingsBucket)

    @Delete
    suspend fun delete(bucket: SavingsBucket)

    @Query("SELECT * FROM savings_buckets WHERE id = :id")
    suspend fun getBucketById(id: String): SavingsBucket?

    @Query("SELECT * FROM savings_buckets ORDER BY created_at DESC")
    fun getAllBuckets(): Flow<List<SavingsBucket>>

    @Query("SELECT * FROM savings_buckets WHERE is_archived = 0 ORDER BY created_at DESC")
    fun getNonArchivedBuckets(): Flow<List<SavingsBucket>>

    @Query("SELECT * FROM savings_buckets WHERE is_archived = 1 ORDER BY created_at DESC")
    fun getArchivedBuckets(): Flow<List<SavingsBucket>>

    @Query("SELECT * FROM savings_buckets WHERE type = :type ORDER BY created_at DESC")
    fun getBucketsByType(type: SavingsBucketType): Flow<List<SavingsBucket>>

    @Query("SELECT * FROM savings_buckets WHERE name LIKE '%' || :searchTerm || '%'")
    fun searchBuckets(searchTerm: String): Flow<List<SavingsBucket>>

    @Query("DELETE FROM savings_buckets")
    suspend fun deleteAll()

    @Query("SELECT * FROM savings_buckets")
    suspend fun getAllBucketsSync(): List<SavingsBucket>
}
