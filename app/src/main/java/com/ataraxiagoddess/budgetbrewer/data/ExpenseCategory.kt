package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "expense_categories",
    indices = [Index(value = ["budgetId"])]
)
data class ExpenseCategory(
    @PrimaryKey
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerialName("budget_id")
    val budgetId: String,
    @SerialName("name")
    val name: String,
    @SerialName("color")
    val color: Int,
    @SerialName("display_order")
    val displayOrder: Int = 0,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)