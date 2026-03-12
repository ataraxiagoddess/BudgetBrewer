package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "daily_checklist",
    indices = [Index(value = ["budgetId"])]
)
data class DailyChecklist(
    @PrimaryKey
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerialName("budget_id")
    val budgetId: String,
    @SerialName("day_of_month")
    val dayOfMonth: Int,
    @SerialName("is_checked")
    val isChecked: Boolean = false,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)