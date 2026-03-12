package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "allocations")
data class Allocation(
    @PrimaryKey
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerialName("budget_id")
    val budgetId: String,
    @SerialName("savings_amount")
    val savingsAmount: Double,
    @SerialName("savings_is_percentage")
    val savingsIsPercentage: Boolean = true,
    @SerialName("spending_amount")
    val spendingAmount: Double,
    @SerialName("spending_is_percentage")
    val spendingIsPercentage: Boolean = true,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)