package com.ataraxiagoddess.budgetbrewer.ui.finances

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.Allocation
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.Expense
import com.ataraxiagoddess.budgetbrewer.data.ExpenseCategory
import com.ataraxiagoddess.budgetbrewer.data.Frequency
import com.ataraxiagoddess.budgetbrewer.data.Income
import com.ataraxiagoddess.budgetbrewer.data.RecurrenceType
import com.ataraxiagoddess.budgetbrewer.data.SyncManager
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseViewModel
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.util.CategoryColors
import com.ataraxiagoddess.budgetbrewer.util.CurrencyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class IncomeExpensesViewModel(
    private val repository: BudgetRepository,
    private val savedStateHandle: SavedStateHandle,
    private val appContext: Context
) : BaseViewModel() {

    private var budgetId: String = savedStateHandle.get<String>("budgetId") ?: ""

    private val _uiState = MutableStateFlow<IncomeExpensesUiState>(IncomeExpensesUiState.Loading)
    val uiState: StateFlow<IncomeExpensesUiState> = _uiState.asStateFlow()

    private val _tipsList = MutableStateFlow<List<Income>>(emptyList())
    val tipsList: StateFlow<List<Income>> = _tipsList.asStateFlow()

    private val _allocation = MutableStateFlow<Allocation?>(null)
    val allocation: StateFlow<Allocation?> = _allocation.asStateFlow()

    init {
        loadData()
    }

    fun updateMonth(month: Month) {
        viewModelScope.launch {
            val newBudgetId = repository.getOrCreateBudgetChain(month.month, month.year)
            budgetId = newBudgetId
            savedStateHandle["budgetId"] = newBudgetId

            val targetExpenses = repository.getExpensesForBudget(budgetId).first()
            val hasRecurring = targetExpenses.any { it.recurrenceType != RecurrenceType.NONE }
            if (!hasRecurring) {
                val previousBudget = repository.findPreviousBudget(month.month, month.year)
                if (previousBudget != null) {
                    repository.propagateRecurringExpenses(previousBudget.id, budgetId)
                }
            }

            loadData()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = IncomeExpensesUiState.Loading
            try {
                val incomes = repository.getIncomesForBudget(budgetId).first()
                val categories = repository.getCategoriesForBudget(budgetId).first()
                val expenses = repository.getExpensesForBudget(budgetId).first()
                val allocation = repository.getAllocationForBudget(budgetId).first()
                _uiState.value = IncomeExpensesUiState.Success(incomes, categories, expenses)
                _tipsList.value = incomes.filter { it.isTips }.sortedBy { it.tipsOrder ?: 0 }
                _allocation.value = allocation
            } catch (e: Exception) {
                _uiState.value = IncomeExpensesUiState.Error("Failed to load data: ${e.message}")
                emitError(R.string.error_load_data, e)
            }
        }
    }

    // Allocation Actions
    fun setSavingsAllocation(amount: Double) {
        safeLaunch(R.string.error_save_allocation) {
            val current = _allocation.value
            if (current != null) {
                val updated = current.copy(savingsAmount = amount, savingsIsPercentage = false)
                repository.updateAllocation(updated)
                // Sync after update
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    SyncManager(appContext).uploadAllocation(updated, userId)
                }
            } else {
                val newAllocation = Allocation(
                    budgetId = budgetId,
                    savingsAmount = amount,
                    spendingAmount = 0.0,
                    savingsIsPercentage = false,
                    spendingIsPercentage = false
                )
                repository.insertAllocation(newAllocation)
                // Sync after insert
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    SyncManager(appContext).uploadAllocation(newAllocation, userId)
                }
            }
            refreshAllocation()
        }
    }

    fun setSpendingAllocation(amount: Double) {
        safeLaunch(R.string.error_save_allocation) {
            val current = _allocation.value
            if (current != null) {
                val updated = current.copy(spendingAmount = amount, spendingIsPercentage = false)
                repository.updateAllocation(updated)
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    SyncManager(appContext).uploadAllocation(updated, userId)
                }
            } else {
                val newAllocation = Allocation(
                    budgetId = budgetId,
                    savingsAmount = 0.0,
                    spendingAmount = amount,
                    savingsIsPercentage = false,
                    spendingIsPercentage = false
                )
                repository.insertAllocation(newAllocation)
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    SyncManager(appContext).uploadAllocation(newAllocation, userId)
                }
            }
            refreshAllocation()
        }
    }

    fun deleteSavingsAllocation() {
        viewModelScope.launch {
            val current = _allocation.value
            if (current != null) {
                val updated = current.copy(savingsAmount = 0.0)
                if (updated.savingsAmount == 0.0 && updated.spendingAmount == 0.0) {
                    repository.deleteAllocation(updated)
                    val userId = AuthManager.getUserId(appContext)
                    if (userId != null) {
                        SyncManager(appContext).deleteAllocation(updated.id, userId)
                    }
                } else {
                    repository.updateAllocation(updated)
                }
                refreshAllocation()
            }
        }
    }

    fun deleteSpendingAllocation() {
        viewModelScope.launch {
            val current = _allocation.value
            if (current != null) {
                val updated = current.copy(spendingAmount = 0.0)
                if (updated.savingsAmount == 0.0 && updated.spendingAmount == 0.0) {
                    repository.deleteAllocation(updated)
                    val userId = AuthManager.getUserId(appContext)
                    if (userId != null) {
                        SyncManager(appContext).deleteAllocation(updated.id, userId)
                    }
                } else {
                    repository.updateAllocation(updated)
                }
                refreshAllocation()
            }
        }
    }

    private suspend fun refreshAllocation() {
        try {
            val alloc = repository.getAllocationForBudget(budgetId).first()
            _allocation.value = alloc
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh allocation")
            emitError(R.string.error_refresh_allocation, e)
            _allocation.value = null
        }
    }

    // Income Actions
    fun deleteIncomesNotOfFrequency(frequency: Frequency) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is IncomeExpensesUiState.Success) {
                val incomesToDelete = currentState.incomes.filter { it.frequency != frequency }
                incomesToDelete.forEach { income ->
                    repository.deleteIncome(income)
                    val userId = AuthManager.getUserId(appContext)
                    if (userId != null) {
                        SyncManager(appContext).deleteIncome(income.id, userId)
                    }
                }
                refreshIncomes()
            }
        }
    }

    fun addIncome(
        sourceName: String,
        amount: Double,
        frequency: Frequency,
        weekNumber: Int? = null,
        currency: String? = null
    ) {
        safeLaunch(R.string.error_add_income) {
            val income = Income(
                budgetId = budgetId,
                sourceName = sourceName,
                amount = amount,
                currency = currency ?: CurrencyPrefs.currentSymbol,
                frequency = frequency,
                weekNumber = weekNumber
            )
            repository.insertIncome(income)
            // Sync after insert
            val userId = AuthManager.getUserId(appContext)
            Timber.d("addIncome: userId = $userId")
            if (userId != null) {
                SyncManager(appContext).uploadIncome(income, userId)
            }
            emitSuccess(UiEvent.IncomeAdded)
            refreshIncomes()
        }
    }

    fun updateIncome(income: Income) {
        safeLaunch(R.string.error_update_income) {
            val updated = income.copy(updatedAt = System.currentTimeMillis())
            repository.updateIncome(income)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadIncome(updated, userId)
            }
            emitSuccess(UiEvent.IncomeUpdated)
            refreshIncomes()
        }
    }

    fun deleteIncome(income: Income) {
        safeLaunch(R.string.error_delete_income) {
            repository.deleteIncome(income)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).deleteIncome(income.id, userId)
            }
            emitSuccess(UiEvent.IncomeDeleted)
            refreshIncomes()
        }
    }

    // Tip Actions
    fun addTip(sourceName: String, amount: Double, tipsOrder: Int, currency: String? = null) {
        safeLaunch(R.string.error_add_tip) {
            val tip = Income(
                budgetId = budgetId,
                sourceName = sourceName,
                amount = amount,
                frequency = Frequency.MONTHLY,
                isTips = true,
                tipsOrder = tipsOrder,
                currency = currency ?: CurrencyPrefs.currentSymbol
            )
            repository.insertIncome(tip)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadIncome(tip, userId)
            }
            emitSuccess(UiEvent.TipAdded)
            refreshIncomes()
        }
    }

    fun updateTip(tip: Income) {
        safeLaunch(R.string.error_update_tip) {
            val updated = tip.copy(updatedAt = System.currentTimeMillis())
            repository.updateIncome(tip)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadIncome(updated, userId)
            }
            emitSuccess(UiEvent.TipUpdated)
            refreshIncomes()
        }
    }

    fun deleteTip(tip: Income) {
        safeLaunch(R.string.error_delete_tip) {
            repository.deleteIncome(tip)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).deleteIncome(tip.id, userId)
            }
            emitSuccess(UiEvent.TipDeleted)
            refreshIncomes()
        }
    }

    // Category Actions
    fun addCategory(name: String) {
        safeLaunch(R.string.error_add_category) {
            val currentCategories = (uiState.value as? IncomeExpensesUiState.Success)?.categories ?: emptyList()
            val colorRes = CategoryColors.colors[currentCategories.size % CategoryColors.colors.size]
            val colorInt = androidx.core.content.ContextCompat.getColor(appContext, colorRes)
            val category = ExpenseCategory(
                budgetId = budgetId,
                name = name,
                color = colorInt,
                displayOrder = currentCategories.size
            )
            repository.insertCategory(category)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadCategory(category, userId)
            }
            emitSuccess(UiEvent.CategoryAdded)
            refreshCategories()
        }
    }

    fun updateCategory(category: ExpenseCategory) {
        safeLaunch(R.string.error_update_category) {
            val updated = category.copy(updatedAt = System.currentTimeMillis())
            repository.updateCategory(category)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadCategory(updated, userId)
            }
            emitSuccess(UiEvent.CategoryUpdated)
            refreshCategories()
        }
    }

    fun deleteCategory(category: ExpenseCategory) {
        safeLaunch(R.string.error_delete_category) {
            repository.deleteCategory(category)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).deleteCategory(category.id, userId)
            }
            emitSuccess(UiEvent.CategoryDeleted)
            refreshCategories()
        }
    }

    // Expense Actions
    fun addExpense(
        categoryId: String,
        description: String,
        amount: Double,
        dueDate: Long,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        recurrenceInterval: Int? = null
    ) {
        safeLaunch(R.string.error_add_expense) {
            val expense = Expense(
                categoryId = categoryId,
                description = description,
                amount = amount,
                dueDate = dueDate,
                recurrenceType = recurrenceType,
                recurrenceInterval = recurrenceInterval
            )
            repository.insertExpense(expense)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadExpense(expense, userId)
            }
            emitSuccess(UiEvent.ExpenseAdded)
            refreshExpenses()
        }
    }

    fun updateExpense(expense: Expense) {
        safeLaunch(R.string.error_update_expense) {
            val updated = expense.copy(updatedAt = System.currentTimeMillis())
            repository.updateExpense(expense)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadExpense(updated, userId)
            }
            emitSuccess(UiEvent.ExpenseUpdated)
            refreshExpenses()
        }
    }

    fun deleteExpense(expense: Expense) {
        safeLaunch(R.string.error_delete_expense) {
            repository.deleteExpense(expense)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).deleteExpense(expense.id, userId)
            }
            emitSuccess(UiEvent.ExpenseDeleted)
            refreshExpenses()
        }
    }

    // Refresh helpers
    private suspend fun refreshIncomes() {
        try {
            val incomes = repository.getIncomesForBudget(budgetId).first()
            val current = _uiState.value
            if (current is IncomeExpensesUiState.Success) {
                _uiState.value = current.copy(incomes = incomes)
            }
            _tipsList.value = incomes.filter { it.isTips }.sortedBy { it.tipsOrder ?: 0 }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh incomes")
            emitError(R.string.error_refresh_incomes, e)
        }
    }

    private suspend fun refreshCategories() {
        try {
            val categories = repository.getCategoriesForBudget(budgetId).first()
            val current = _uiState.value
            if (current is IncomeExpensesUiState.Success) {
                _uiState.value = current.copy(categories = categories)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh categories")
            emitError(R.string.error_refresh_categories, e)
        }
    }

    private suspend fun refreshExpenses() {
        try {
            val expenses = repository.getExpensesForBudget(budgetId).first()
            val current = _uiState.value
            if (current is IncomeExpensesUiState.Success) {
                _uiState.value = current.copy(expenses = expenses)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh expenses")
            emitError(R.string.error_refresh_expenses, e)
        }
    }

    fun refreshData() {
        loadData()
    }
}