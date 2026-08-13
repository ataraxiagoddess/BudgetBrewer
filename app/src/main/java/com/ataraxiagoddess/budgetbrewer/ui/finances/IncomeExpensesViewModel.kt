/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.finances

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.Allocation
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.DailyChecklist
import com.ataraxiagoddess.budgetbrewer.data.Expense
import com.ataraxiagoddess.budgetbrewer.data.ExpenseCategory
import com.ataraxiagoddess.budgetbrewer.data.Frequency
import com.ataraxiagoddess.budgetbrewer.data.Income
import com.ataraxiagoddess.budgetbrewer.data.MonthSettings
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
import java.util.Calendar

class IncomeExpensesViewModel(
    private val repository: BudgetRepository,
    private val savedStateHandle: SavedStateHandle,
    private val appContext: Context,
) : BaseViewModel() {
    private var budgetId: String = savedStateHandle.get<String>("budgetId") ?: ""

    private val _uiState = MutableStateFlow<IncomeExpensesUiState>(IncomeExpensesUiState.Loading)
    val uiState: StateFlow<IncomeExpensesUiState> = _uiState.asStateFlow()

    private val _tipsList = MutableStateFlow<List<Income>>(emptyList())
    val tipsList: StateFlow<List<Income>> = _tipsList.asStateFlow()

    private val _allocation = MutableStateFlow<Allocation?>(null)
    val allocation: StateFlow<Allocation?> = _allocation.asStateFlow()

    private val _monthSettings = MutableStateFlow<MonthSettings?>(null)
    val monthSettings: StateFlow<MonthSettings?> = _monthSettings.asStateFlow()

    private val _selectedPayFrequency = MutableStateFlow<Frequency?>(null)
    val selectedPayFrequency: StateFlow<Frequency?> = _selectedPayFrequency.asStateFlow()

    init {
        loadData()
    }

    private fun normalizeToMidnight(timestamp: Long): Long =
        Calendar
            .getInstance()
            .apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

    // ---------- Public API ----------

    fun updateMonth(month: Month) {
        viewModelScope.launch {
            _selectedPayFrequency.value = null
            val (newBudgetId, wasCreated) = repository.getOrCreateBudgetChain(month.month, month.year)
            budgetId = newBudgetId
            savedStateHandle["budgetId"] = newBudgetId

            val budget = repository.getBudgetById(newBudgetId)
            val userId = AuthManager.getUserId(appContext)
            if (budget != null && userId != null) {
                SyncManager(appContext).uploadBudget(budget, userId)
            }

            if (!wasCreated) {
                val previousBudget = repository.findPreviousBudget(month.month, month.year)
                if (previousBudget != null) {
                    repository.propagateRecurringExpenses(previousBudget.id, budgetId)
                    if (userId != null) {
                        val expenses = repository.getExpensesForBudget(budgetId).first()
                        expenses.forEach { expense ->
                            SyncManager(appContext).uploadExpense(expense, userId)
                        }
                        val checklistItems = repository.getDailyChecklist(budgetId).first()
                        checklistItems.forEach { item ->
                            SyncManager(appContext).uploadDailyChecklistItem(item, userId)
                        }
                    }
                }
            }
            loadData()
        }
    }

    fun refreshData() {
        loadData()
    }

    // ---------- Allocation ----------

    fun setSavingsAllocation(amount: Double) {
        safeLaunch(R.string.error_save_allocation) {
            val distributedTotal = repository.getTotalDistributedToBuckets()
            if (amount < distributedTotal) {
                emitError(
                    R.string.savings_allocation_conflict,
                    Exception("Requested $amount but distributed $distributedTotal"),
                )
                _event.emit(UiEvent.SavingsAllocationConflict(amount, distributedTotal))
                return@safeLaunch
            }

            val current = _allocation.value
            if (current != null) {
                val updated = current.copy(savingsAmount = amount, savingsIsPercentage = false)
                repository.updateAllocation(updated)
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    SyncManager(appContext).uploadAllocation(updated, userId)
                }
            } else {
                val newAllocation =
                    Allocation(
                        budgetId = budgetId,
                        savingsAmount = amount,
                        spendingAmount = 0.0,
                        savingsIsPercentage = false,
                        spendingIsPercentage = false,
                    )
                repository.insertAllocation(newAllocation)
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    val budget = repository.getBudgetById(budgetId)
                    if (budget != null) {
                        SyncManager(appContext).uploadBudget(budget, userId)
                    }
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
                val newAllocation =
                    Allocation(
                        budgetId = budgetId,
                        savingsAmount = 0.0,
                        spendingAmount = amount,
                        savingsIsPercentage = false,
                        spendingIsPercentage = false,
                    )
                repository.insertAllocation(newAllocation)
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    val budget = repository.getBudgetById(budgetId)
                    if (budget != null) {
                        SyncManager(appContext).uploadBudget(budget, userId)
                    }
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
                val userId = AuthManager.getUserId(appContext)
                if (updated.savingsAmount == 0.0 && updated.spendingAmount == 0.0) {
                    repository.deleteAllocation(updated)
                    if (userId != null) {
                        SyncManager(appContext).deleteAllocation(updated.id, userId)
                    }
                } else {
                    repository.updateAllocation(updated)
                    if (userId != null) {
                        SyncManager(appContext).uploadAllocation(updated, userId)
                    }
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
                val userId = AuthManager.getUserId(appContext)
                if (updated.savingsAmount == 0.0 && updated.spendingAmount == 0.0) {
                    repository.deleteAllocation(updated)
                    if (userId != null) {
                        SyncManager(appContext).deleteAllocation(updated.id, userId)
                    }
                } else {
                    repository.updateAllocation(updated)
                    if (userId != null) {
                        SyncManager(appContext).uploadAllocation(updated, userId)
                    }
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

    // ---------- Income ----------

    fun deleteIncomesNotOfFrequency(frequency: Frequency) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is IncomeExpensesUiState.Success) {
                val incomesToDelete = currentState.incomes.filter { !it.isTips && it.frequency != frequency }
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
        currency: String? = null,
    ) {
        safeLaunch(R.string.error_add_income) {
            val income =
                Income(
                    budgetId = budgetId,
                    sourceName = sourceName,
                    amount = amount,
                    currency = currency ?: CurrencyPrefs.currentCode,
                    frequency = frequency,
                    weekNumber = weekNumber,
                )
            repository.insertIncome(income)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                val budget = repository.getBudgetById(budgetId)
                if (budget != null) {
                    SyncManager(appContext).uploadBudget(budget, userId)
                }
                SyncManager(appContext).uploadIncome(income, userId)
            }
            emitSuccess(UiEvent.IncomeAdded)
            refreshIncomes()
        }
    }

    fun updateIncome(income: Income) {
        safeLaunch(R.string.error_update_income) {
            val updated = income.copy(updatedAt = System.currentTimeMillis())
            repository.updateIncome(updated)
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

    // ---------- Tips ----------

    fun addTip(
        sourceName: String,
        amount: Double,
        tipsOrder: Int,
        currency: String? = null,
    ) {
        safeLaunch(R.string.error_add_tip) {
            val tip =
                Income(
                    budgetId = budgetId,
                    sourceName = sourceName,
                    amount = amount,
                    frequency = Frequency.MONTHLY,
                    isTips = true,
                    tipsOrder = tipsOrder,
                    currency = currency ?: CurrencyPrefs.currentCode,
                )
            repository.insertIncome(tip)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                val budget = repository.getBudgetById(budgetId)
                if (budget != null) {
                    SyncManager(appContext).uploadBudget(budget, userId)
                }
                SyncManager(appContext).uploadIncome(tip, userId)
            }
            emitSuccess(UiEvent.TipAdded)
            refreshIncomes()
        }
    }

    fun updateTip(tip: Income) {
        safeLaunch(R.string.error_update_tip) {
            val updated = tip.copy(updatedAt = System.currentTimeMillis())
            repository.updateIncome(updated)
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

    // ---------- Categories ----------

    fun addCategory(name: String) {
        safeLaunch(R.string.error_add_category) {
            val currentCategories = (uiState.value as? IncomeExpensesUiState.Success)?.categories ?: emptyList()
            val colorRes = CategoryColors.colors[currentCategories.size % CategoryColors.colors.size]
            val colorInt =
                androidx.core.content.ContextCompat
                    .getColor(appContext, colorRes)
            val category =
                ExpenseCategory(
                    budgetId = budgetId,
                    name = name,
                    color = colorInt,
                    displayOrder = currentCategories.size,
                )
            repository.insertCategory(category)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                val budget = repository.getBudgetById(budgetId)
                if (budget != null) {
                    SyncManager(appContext).uploadBudget(budget, userId)
                }
                SyncManager(appContext).uploadCategory(category, userId)
            }
            emitSuccess(UiEvent.CategoryAdded)
            refreshCategories()
        }
    }

    fun updateCategory(category: ExpenseCategory) {
        safeLaunch(R.string.error_update_category) {
            val updated = category.copy(updatedAt = System.currentTimeMillis())
            repository.updateCategory(updated)
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

    // ---------- Expenses ----------

    private suspend fun ensureChecklistItemForExpense(expense: Expense) {
        val day = Calendar.getInstance().apply { timeInMillis = expense.dueDate }.get(Calendar.DAY_OF_MONTH)
        val existing = repository.getChecklistItem(budgetId, day)
        if (existing == null) {
            val newItem =
                DailyChecklist(
                    budgetId = budgetId,
                    dayOfMonth = day,
                    isChecked = false,
                )
            repository.insertChecklistItem(newItem)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadDailyChecklistItem(newItem, userId)
            }
        }
    }

    fun addExpense(
        categoryId: String,
        description: String,
        amount: Double,
        dueDate: Long,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        recurrenceInterval: Int? = null,
    ) {
        safeLaunch(R.string.error_add_expense) {
            val now = System.currentTimeMillis()
            val master =
                Expense(
                    categoryId = categoryId,
                    description = description,
                    amount = amount,
                    dueDate = dueDate,
                    recurrenceType = recurrenceType,
                    recurrenceInterval = recurrenceInterval,
                    createdAt = now,
                    updatedAt = now,
                )
            repository.insertExpense(master)
            ensureChecklistItemForExpense(master)

            val userId = AuthManager.getUserId(appContext)

            if (recurrenceType == RecurrenceType.EVERY_X_DAYS && recurrenceInterval != null) {
                val budget = repository.getBudgetById(budgetId)
                if (budget != null) {
                    val dates =
                        repository.generateOccurrenceDatesInMonth(
                            baseDate = dueDate,
                            intervalDays = recurrenceInterval,
                            targetMonth = budget.month,
                            targetYear = budget.year,
                        )
                    val masterDueDate = normalizeToMidnight(dueDate)
                    for (date in dates) {
                        if (date == masterDueDate) continue
                        val child =
                            Expense(
                                categoryId = categoryId,
                                description = description,
                                amount = amount,
                                dueDate = date,
                                recurrenceType = recurrenceType,
                                recurrenceInterval = recurrenceInterval,
                                sourceExpenseId = master.id,
                                createdAt = now,
                                updatedAt = now,
                            )
                        repository.insertExpense(child)
                        ensureChecklistItemForExpense(child)
                        if (userId != null) {
                            SyncManager(appContext).uploadExpense(child, userId)
                        }
                    }
                }
            }

            if (userId != null) {
                val budget = repository.getBudgetById(budgetId)
                if (budget != null) {
                    SyncManager(appContext).uploadBudget(budget, userId)
                }
                SyncManager(appContext).uploadExpense(master, userId)
            }
            emitSuccess(UiEvent.ExpenseAdded)
            refreshExpenses()
        }
    }

    fun updateExpense(expense: Expense) {
        safeLaunch(R.string.error_update_expense) {
            val now = System.currentTimeMillis()
            val userId = AuthManager.getUserId(appContext)

            val originalExpense =
                repository
                    .getExpensesForBudget(budgetId)
                    .first()
                    .find { it.id == expense.id }
            val originalDueDate = originalExpense?.dueDate

            val updated =
                expense.copy(
                    updatedAt = now,
                    isOverridden = expense.sourceExpenseId != null || expense.isOverridden,
                )
            repository.updateExpense(updated)

            if (userId != null) {
                SyncManager(appContext).uploadExpense(updated, userId)
            }

            // Handle checklist changes when due date shifts
            if (originalDueDate != null && originalDueDate != expense.dueDate) {
                val oldDay = Calendar.getInstance().apply { timeInMillis = originalDueDate }.get(Calendar.DAY_OF_MONTH)

                val remainingOnOldDay =
                    repository
                        .getExpensesForBudget(budgetId)
                        .first()
                        .any {
                            Calendar.getInstance().apply { timeInMillis = it.dueDate }.get(Calendar.DAY_OF_MONTH) == oldDay &&
                                it.id != expense.id
                        }
                if (!remainingOnOldDay) {
                    val oldChecklistItem = repository.getChecklistItem(budgetId, oldDay)
                    if (oldChecklistItem != null) {
                        repository.deleteChecklistItem(oldChecklistItem)
                        if (userId != null) {
                            SyncManager(appContext).deleteDailyChecklistItem(oldChecklistItem.id, userId)
                        }
                    }
                }
                ensureChecklistItemForExpense(expense)
            }

            // Handle EVERY_X_DAYS cascade
            if (expense.recurrenceType == RecurrenceType.EVERY_X_DAYS) {
                val budget = repository.getBudgetById(budgetId)
                if (budget != null) {
                    val masterId = expense.sourceExpenseId ?: expense.id
                    val allExpenses = repository.getExpensesForBudget(budgetId).first()
                    val sameMonthChildren =
                        allExpenses.filter {
                            it.sourceExpenseId == masterId &&
                                it.id != expense.id &&
                                it.recurrenceType == RecurrenceType.EVERY_X_DAYS
                        }

                    // Delete children that come after the edited expense in the same month
                    for (child in sameMonthChildren) {
                        if (child.dueDate > expense.dueDate) {
                            val deletedChecklistId = repository.deleteExpense(child)
                            if (userId != null) {
                                SyncManager(appContext).deleteExpense(child.id, userId)
                                if (deletedChecklistId != null) {
                                    SyncManager(appContext).deleteDailyChecklistItem(deletedChecklistId, userId)
                                }
                            }
                        }
                    }

                    // Generate new forward dates from the edited expense's due date
                    val cal = Calendar.getInstance().apply { timeInMillis = expense.dueDate }
                    val month = cal.get(Calendar.MONTH) + 1
                    val year = cal.get(Calendar.YEAR)

                    val newDates =
                        repository.generateOccurrenceDatesInMonth(
                            baseDate = expense.dueDate,
                            intervalDays = expense.recurrenceInterval ?: 30,
                            targetMonth = month,
                            targetYear = year,
                        )

                    val editedDueDate = normalizeToMidnight(expense.dueDate)
                    for (date in newDates) {
                        if (date == editedDueDate) continue
                        val child =
                            Expense(
                                categoryId = expense.categoryId,
                                description = expense.description,
                                amount = expense.amount,
                                dueDate = date,
                                recurrenceType = expense.recurrenceType,
                                recurrenceInterval = expense.recurrenceInterval,
                                sourceExpenseId = masterId,
                                createdAt = now,
                                updatedAt = now,
                            )
                        repository.insertExpense(child)
                        ensureChecklistItemForExpense(child)
                        if (userId != null) {
                            SyncManager(appContext).uploadExpense(child, userId)
                        }
                    }
                }
            }

            emitSuccess(UiEvent.ExpenseUpdated)
            refreshExpenses()
        }
    }

    fun deleteExpense(expense: Expense) {
        safeLaunch(R.string.error_delete_expense) {
            val deletedChecklistId = repository.deleteExpense(expense)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).deleteExpense(expense.id, userId)
                if (deletedChecklistId != null) {
                    SyncManager(appContext).deleteDailyChecklistItem(deletedChecklistId, userId)
                }
            }
            emitSuccess(UiEvent.ExpenseDeleted)
            refreshExpenses()
        }
    }

    // ---------- Settings ----------

    fun updateTipsEnabled(enabled: Boolean) {
        safeLaunch(R.string.error_update_settings) {
            val current = _monthSettings.value ?: return@safeLaunch
            val updated =
                current.copy(
                    tipsEnabled = enabled,
                    updatedAt = System.currentTimeMillis(),
                )
            repository.insertOrUpdateMonthSettings(updated)
            _monthSettings.value = updated

            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadMonthSetting(updated, userId)
            }
        }
    }

    fun setSelectedPayFrequency(frequency: Frequency) {
        _selectedPayFrequency.value = frequency
    }

    fun updatePayFrequency(frequency: Frequency) {
        safeLaunch(R.string.error_update_settings) {
            val current = _monthSettings.value ?: return@safeLaunch
            val updated =
                current.copy(
                    payFrequency = frequency.name,
                    updatedAt = System.currentTimeMillis(),
                )
            repository.insertOrUpdateMonthSettings(updated)
            _monthSettings.value = updated

            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadMonthSetting(updated, userId)
            }
        }
    }

    // ---------- Private Refresh Helpers ----------

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

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = IncomeExpensesUiState.Loading
            try {
                val budget = repository.getBudgetById(budgetId)
                val userId = AuthManager.getUserId(appContext)
                if (budget != null && userId != null) {
                    SyncManager(appContext).uploadBudget(budget, userId)
                }
                val incomes = repository.getIncomesForBudget(budgetId).first()
                val categories = repository.getCategoriesForBudget(budgetId).first()
                val expenses = repository.getExpensesForBudget(budgetId).first()
                val allocation = repository.getAllocationForBudget(budgetId).first()
                val settings = repository.getMonthSettings(budgetId).first()
                _tipsList.value = incomes.filter { it.isTips }.sortedBy { it.tipsOrder ?: 0 }
                _allocation.value = allocation
                _monthSettings.value = settings
                _uiState.value = IncomeExpensesUiState.Success(incomes, categories, expenses)
            } catch (e: Exception) {
                _uiState.value = IncomeExpensesUiState.Error("Failed to load data: ${e.message}")
                emitError(R.string.error_load_data, e)
            }
        }
    }
}
