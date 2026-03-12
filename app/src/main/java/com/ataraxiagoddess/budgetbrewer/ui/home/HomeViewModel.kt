package com.ataraxiagoddess.budgetbrewer.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseViewModel
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: BudgetRepository,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel() {
    private var budgetId: String = savedStateHandle.get<String>("budgetId") ?: ""
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _timeframe = MutableStateFlow(Timeframe.ONE_MONTH)
    private var selectedMonth: Month = Month.current()

    enum class Timeframe {
        ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR
    }

    init {
        viewModelScope.launch {
            loadData()
        }
    }

    fun setTimeframe(timeframe: Timeframe) {
        _timeframe.value = timeframe
        viewModelScope.launch {
            loadData()
        }
    }

    fun updateMonth(month: Month) {
        selectedMonth = month
        viewModelScope.launch {
            val newBudgetId = repository.getOrCreateBudgetChain(month.month, month.year)
            budgetId = newBudgetId
            savedStateHandle["budgetId"] = newBudgetId
            loadData()
        }
    }

    private suspend fun loadData() {
        _uiState.value = HomeUiState.Loading
        try {
            val incomes = repository.getIncomesForBudget(budgetId).first()
            val expenses = repository.getExpensesForBudget(budgetId).first()
            val categories = repository.getCategoriesForBudget(budgetId).first()
            val allocation = repository.getAllocationForBudget(budgetId).first()

            val totalIncome = incomes.sumOf { it.amount }
            val totalExpenses = expenses.sumOf { it.amount }

            val expensesByCategory = categories.map { category ->
                val categoryTotal = expenses
                    .filter { it.categoryId == category.id }
                    .sumOf { it.amount }
                val percentage = if (totalExpenses > 0) categoryTotal / totalExpenses * 100 else 0.0
                CategoryExpense(category, categoryTotal, percentage)
            }.filter { it.amount > 0 }

            val savingsTarget = totalIncome * 0.2
            val savingsAmount = allocation?.savingsAmount ?: 0.0

            // Compute spending history for the selected timeframe
            val spendingHistory = buildSpendingHistory()

            _uiState.value = HomeUiState.Success(
                totalIncome = totalIncome,
                totalExpenses = totalExpenses,
                expensesByCategory = expensesByCategory,
                savingsAmount = savingsAmount,
                savingsTarget = savingsTarget,
                spendingHistory = spendingHistory
            )
        } catch (e: Exception) {
            _uiState.value = HomeUiState.Error("Failed to load data: ${e.message}")
            emitError(R.string.error_load_data, e)
        }
    }

    private suspend fun buildSpendingHistory(): List<MonthlySpending> {
        val monthsToInclude = when (_timeframe.value) {
            Timeframe.ONE_MONTH -> 2
            Timeframe.THREE_MONTHS -> 3
            Timeframe.SIX_MONTHS -> 6
            Timeframe.ONE_YEAR -> 12
        }
        val result = mutableListOf<MonthlySpending>()
        for (i in 0 until monthsToInclude) {
            var month = selectedMonth.month - i
            var year = selectedMonth.year
            while (month <= 0) {
                month += 12
                year -= 1
            }
            // Get budget ID if it exists, otherwise 0
            val budgetId = repository.getBudgetIdIfExists(month, year)
            val amount = if (budgetId != null) {
                repository.getSpendingTotalForBudget(budgetId)
            } else {
                0.0
            }
            result.add(0, MonthlySpending(month, year, amount))
        }
        return result
    }
}