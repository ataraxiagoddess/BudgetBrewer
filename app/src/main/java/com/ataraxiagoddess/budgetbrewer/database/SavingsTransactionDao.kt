package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.*
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsTransactionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: SavingsTransaction): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<SavingsTransaction>): List<Long>
    
    @Query("SELECT * FROM savings_transactions WHERE bucket_id = :bucketId ORDER BY date DESC")
    fun getTransactionsByBucket(bucketId: Long): Flow<List<SavingsTransaction>>
    
    @Query("SELECT * FROM savings_transactions WHERE bucket_id = :bucketId AND date = :date ORDER BY date DESC")
    suspend fun getTransactionsByBucketAndDate(bucketId: Long, date: Long): List<SavingsTransaction>
    
    @Query("SELECT * FROM savings_transactions WHERE bucket_id = :bucketId AND date <= :endDate ORDER BY date DESC")
    fun getTransactionsByBucketUntil(bucketId: Long, endDate: Long): Flow<List<SavingsTransaction>>
    
    @Query("SELECT * FROM savings_transactions WHERE date = :date ORDER BY date DESC")
    suspend fun getTransactionsByDate(date: Long): List<SavingsTransaction>
    
    @Query("SELECT * FROM savings_transactions WHERE bucket_id = :bucketId AND type = :type ORDER BY date DESC")
    fun getTransactionsByBucketAndType(bucketId: Long, type: SavingsTransactionType): Flow<List<SavingsTransaction>>
}
