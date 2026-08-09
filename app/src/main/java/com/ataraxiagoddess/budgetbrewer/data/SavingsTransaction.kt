/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

@file:Suppress("PropertyName")
package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "savings_transactions",
    indices = [
        Index(value = ["bucket_id"]),
        Index(value = ["date"]),
        Index(value = ["type"])
    ]
)
data class SavingsTransaction(
    @PrimaryKey
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerialName("bucket_id")
    val bucket_id: String,

    @SerialName("amount")
    val amount: Double,

    @SerialName("date")
    val date: Long,

    @SerialName("type")
    val type: SavingsTransactionType,

    @SerialName("created_at")
    val created_at: Long = System.currentTimeMillis(),

    @SerialName("updated_at")
    val updated_at: Long = System.currentTimeMillis()
)

@Serializable
enum class SavingsTransactionType {
    ALLOCATION,
    DEDUCTION,
    WITHDRAWAL
}
