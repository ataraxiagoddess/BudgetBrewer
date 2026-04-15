package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.*
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucketType
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsBucketDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBucket(bucket: SavingsBucket): Long
    
    @Update
    suspend fun updateBucket(bucket: SavingsBucket)
    
    @Delete
    suspend fun deleteBucket(bucket: SavingsBucket)
    
    @Query("SELECT * FROM savings_buckets WHERE id = :id")
    suspend fun getBucketById(id: Long): SavingsBucket?
    
    @Query("SELECT * FROM savings_buckets ORDER BY created_at DESC")
    fun getAllBuckets(): Flow<List<SavingsBucket>>
    
    @Query("SELECT * FROM savings_buckets WHERE type = :type ORDER BY created_at DESC")
    fun getBucketsByType(type: SavingsBucketType): Flow<List<SavingsBucket>>
    
    @Query("SELECT * FROM savings_buckets WHERE name LIKE '%' || :searchTerm || '%'")
    fun searchBuckets(searchTerm: String): Flow<List<SavingsBucket>>
}
