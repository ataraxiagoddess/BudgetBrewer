package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerialName("budget_id")
    val budgetId: String,
    @SerialName("source_name")
    val sourceName: String,
    @SerialName("currency")
    val currency: String = "$",
    @SerialName("amount")
    val amount: Double,
    @SerialName("frequency")
    val frequency: Frequency,
    @SerialName("is_tips")
    val isTips: Boolean = false,
    @SerialName("week_number")
    val weekNumber: Int? = null,
    @SerialName("tips_order")
    val tipsOrder: Int? = null,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class Frequency {
    MONTHLY, WEEKLY, BIWEEKLY
}