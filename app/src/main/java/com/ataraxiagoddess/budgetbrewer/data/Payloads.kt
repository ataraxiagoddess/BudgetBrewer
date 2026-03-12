package com.ataraxiagoddess.budgetbrewer.data

import kotlinx.serialization.Serializable

@Serializable
data class BudgetPayload(
    val id: String,
    val month: Int,
    val year: Int,
    val created_at: Long,
    val updated_at: Long,
    val user_id: String
)

@Serializable
data class IncomePayload(
    val id: String,
    val budget_id: String,
    val source_name: String,
    val currency: String,
    val amount: Double,
    val frequency: String,
    val is_tips: Boolean,
    val week_number: Int?,
    val tips_order: Int?,
    val created_at: Long,
    val updated_at: Long,
    val user_id: String
)

@Serializable
data class ExpenseCategoryPayload(
    val id: String,
    val budget_id: String,
    val name: String,
    val color: Int,
    val display_order: Int,
    val created_at: Long,
    val updated_at: Long,
    val user_id: String
)

@Serializable
data class ExpensePayload(
    val id: String,
    val category_id: String,
    val description: String,
    val amount: Double,
    val due_date: Long,
    val recurrence_type: String,
    val recurrence_interval: Int?,
    val created_at: Long,
    val is_active: Boolean,
    val updated_at: Long,
    val user_id: String
)

@Serializable
data class AllocationPayload(
    val id: String,
    val budget_id: String,
    val savings_amount: Double,
    val savings_is_percentage: Boolean,
    val spending_amount: Double,
    val spending_is_percentage: Boolean,
    val created_at: Long,
    val updated_at: Long,
    val user_id: String
)

@Serializable
data class DailyChecklistPayload(
    val id: String,
    val budget_id: String,
    val day_of_month: Int,
    val is_checked: Boolean,
    val updated_at: Long,
    val user_id: String
)

@Serializable
data class SpendingEntryPayload(
    val id: String,
    val budget_id: String,
    val date: Long,
    val source: String,
    val amount: Double,
    val created_at: Long,
    val updated_at: Long,
    val user_id: String
)

@Serializable
data class MonthSettingsPayload(
    val id: String,
    val budget_id: String,
    val month_start_amount: Double,
    val month_start_overridden: Boolean,
    val created_at: Long,
    val updated_at: Long,
    val user_id: String
)

@Serializable
data class DailyIncomeAssignmentPayload(
    val id: String,
    val budget_id: String,
    val income_id: String,
    val day_of_month: Int,
    val created_at: Long,
    val updated_at: Long,
    val user_id: String
)