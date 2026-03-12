package com.ataraxiagoddess.budgetbrewer.ui.home

import com.ataraxiagoddess.budgetbrewer.data.ExpenseCategory

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val totalIncome: Double = 0.0,
        val totalExpenses: Double = 0.0,
        val expensesByCategory: List<CategoryExpense> = emptyList(),
        val savingsAmount: Double = 0.0,
        val savingsTarget: Double = 0.0,
        val spendingHistory: List<MonthlySpending> = emptyList()
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

data class CategoryExpense(
    val category: ExpenseCategory,
    val amount: Double,
    val percentage: Double
)

data class MonthlySpending(
    val month: Int,
    val year: Int,
    val amount: Double
)