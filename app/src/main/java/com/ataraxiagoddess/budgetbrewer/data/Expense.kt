package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID

@Serializable
enum class RecurrenceType {
    NONE,
    MONTHLY_SAME_DAY,
    EVERY_X_DAYS
}

@Serializable
@Entity(
    tableName = "expenses",
    indices = [Index(value = ["categoryId"])]
)
data class Expense(
    @PrimaryKey
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("description")
    val description: String,
    @SerialName("amount")
    val amount: Double,
    @SerialName("due_date")
    val dueDate: Long,
    @SerialName("recurrence_type")
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    @SerialName("recurrence_interval")
    val recurrenceInterval: Int? = null,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("is_active")
    val isActive: Boolean = true
) {
    @Suppress("unused")
    fun getDayOfMonth(): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = dueDate }
        return cal.get(Calendar.DAY_OF_MONTH)
    }
}