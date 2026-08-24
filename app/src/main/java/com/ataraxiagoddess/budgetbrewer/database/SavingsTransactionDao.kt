/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: SavingsTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transactions: List<SavingsTransaction>)

    @Update
    suspend fun updateTransaction(transaction: SavingsTransaction)

    @Query("DELETE FROM savings_transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: String)

    @Query(
        "SELECT COALESCE(SUM(amount), 0.0) FROM savings_transactions WHERE bucket_id = :bucketId"
    )
    suspend fun getTotalForBucket(bucketId: String): Double

    @Query("SELECT * FROM savings_transactions WHERE bucket_id = :bucketId ORDER BY date DESC")
    fun getTransactionsByBucket(bucketId: String): Flow<List<SavingsTransaction>>

    @Query("SELECT * FROM savings_transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): SavingsTransaction?

    @Query(
        "SELECT * FROM savings_transactions WHERE bucket_id = :bucketId AND date = :date ORDER BY date DESC"
    )
    suspend fun getTransactionsByBucketAndDate(
        bucketId: String,
        date: Long
    ): List<SavingsTransaction>

    @Query(
        "SELECT * FROM savings_transactions WHERE bucket_id = :bucketId AND date <= :endDate ORDER BY date DESC"
    )
    fun getTransactionsByBucketUntil(
        bucketId: String,
        endDate: Long
    ): Flow<List<SavingsTransaction>>

    @Query("SELECT * FROM savings_transactions WHERE date = :date ORDER BY date DESC")
    suspend fun getTransactionsByDate(date: Long): List<SavingsTransaction>

    @Query(
        "SELECT * FROM savings_transactions WHERE bucket_id = :bucketId AND type = :type ORDER BY date DESC"
    )
    fun getTransactionsByBucketAndType(
        bucketId: String,
        type: SavingsTransactionType
    ): Flow<List<SavingsTransaction>>

    @Query("DELETE FROM savings_transactions WHERE bucket_id = :bucketId")
    suspend fun deleteByBucketId(bucketId: String)

    @Query("DELETE FROM savings_transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM savings_transactions")
    suspend fun getAllTransactionsSync(): List<SavingsTransaction>

    @Query("SELECT * FROM savings_transactions")
    fun getAllTransactions(): Flow<List<SavingsTransaction>>
}
