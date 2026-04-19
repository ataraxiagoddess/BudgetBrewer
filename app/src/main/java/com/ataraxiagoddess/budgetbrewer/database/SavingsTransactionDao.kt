package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.*
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: SavingsTransaction): String  // ✅ Return String ID

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transactions: List<SavingsTransaction>): List<String>

    @Query("SELECT * FROM savings_transactions WHERE bucket_id = :bucketId ORDER BY date DESC")
    fun getTransactionsByBucket(bucketId: String): Flow<List<SavingsTransaction>>  // ✅ String parameter

    @Query("SELECT * FROM savings_transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): SavingsTransaction?

    @Query("SELECT * FROM savings_transactions WHERE bucket_id = :bucketId AND date = :date ORDER BY date DESC")
    suspend fun getTransactionsByBucketAndDate(bucketId: String, date: Long): List<SavingsTransaction>

    @Query("SELECT * FROM savings_transactions WHERE bucket_id = :bucketId AND date <= :endDate ORDER BY date DESC")
    fun getTransactionsByBucketUntil(bucketId: String, endDate: Long): Flow<List<SavingsTransaction>>

    @Query("SELECT * FROM savings_transactions WHERE date = :date ORDER BY date DESC")
    suspend fun getTransactionsByDate(date: Long): List<SavingsTransaction>

    @Query("SELECT * FROM savings_transactions WHERE bucket_id = :bucketId AND type = :type ORDER BY date DESC")
    fun getTransactionsByBucketAndType(bucketId: String, type: SavingsTransactionType): Flow<List<SavingsTransaction>>

    // ✅ Manual cascade delete (since we removed @ForeignKey)
    @Query("DELETE FROM savings_transactions WHERE bucket_id = :bucketId")
    suspend fun deleteByBucketId(bucketId: String)

    @Query("DELETE FROM savings_transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM savings_transactions")
    suspend fun getAllTransactionsSync(): List<SavingsTransaction>
}