package com.ataraxiagoddess.budgetbrewer.ui.finances

import com.ataraxiagoddess.budgetbrewer.data.Expense
import com.ataraxiagoddess.budgetbrewer.data.ExpenseCategory
import com.ataraxiagoddess.budgetbrewer.data.Income

sealed class IncomeExpensesUiState {
    object Loading : IncomeExpensesUiState()
    data class Success(
        val incomes: List<Income> = emptyList(),
        val categories: List<ExpenseCategory> = emptyList(),
        val expenses: List<Expense> = emptyList()
    ) : IncomeExpensesUiState()
    data class Error(val message: String) : IncomeExpensesUiState()
}