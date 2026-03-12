package com.ataraxiagoddess.budgetbrewer.ui.expenses

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.DailyChecklist
import com.ataraxiagoddess.budgetbrewer.data.Expense
import com.ataraxiagoddess.budgetbrewer.data.RecurrenceType
import com.ataraxiagoddess.budgetbrewer.data.SyncManager
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseViewModel
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MonthlyExpenseListViewModel(
    private val repository: BudgetRepository,
    private val savedStateHandle: SavedStateHandle,
    private val appContext: Context
) : BaseViewModel() {

    private var budgetId: String = savedStateHandle.get<String>("budgetId") ?: ""

    private val _uiState = MutableStateFlow<MonthlyExpenseListUiState>(MonthlyExpenseListUiState.Loading)
    val uiState: StateFlow<MonthlyExpenseListUiState> = _uiState.asStateFlow()

    data class DayExpenses(
        val day: Int,
        val expenses: List<Expense>,
        val isChecked: Boolean = false,
        val formattedExpenses: CharSequence = ""
    )

    sealed class MonthlyExpenseListUiState {
        object Loading : MonthlyExpenseListUiState()
        data class Success(
            val days: List<DayExpenses>,
            val totalAmount: Double,
            val remainingAmount: Double
        ) : MonthlyExpenseListUiState()
        data class Error(val message: String) : MonthlyExpenseListUiState()
    }

    fun updateMonth(month: Month) {
        viewModelScope.launch {
            val newBudgetId = repository.getOrCreateBudgetChain(month.month, month.year)
            budgetId = newBudgetId
            savedStateHandle["budgetId"] = newBudgetId
            loadData()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = MonthlyExpenseListUiState.Loading
            try {
                val expenses = repository.getExpensesForBudget(budgetId).first()
                val checklist = repository.getDailyChecklist(budgetId).first().associate { it.dayOfMonth to it.isChecked }
                val budget = repository.getBudgetById(budgetId) ?: return@launch

                val (days, totalAmount, remainingAmount) = withContext(Dispatchers.Default) {
                    val dayMap = mutableMapOf<Int, MutableList<Expense>>()
                    expenses.forEach { expense ->
                        val day = Calendar.getInstance().apply { timeInMillis = expense.dueDate }.get(Calendar.DAY_OF_MONTH)
                        dayMap.getOrPut(day) { mutableListOf() }.add(expense)
                    }

                    val maxDays = getDaysInMonth(budget.year, budget.month)
                    val days = (1..maxDays).map { day ->
                        val dayExpenses = dayMap[day] ?: emptyList()
                        val formatted = buildFormattedExpenses(dayExpenses)
                        DayExpenses(
                            day = day,
                            expenses = dayExpenses,
                            isChecked = checklist[day] ?: false,
                            formattedExpenses = formatted
                        )
                    }

                    val totalAmount = expenses.sumOf { it.amount }
                    val checkedDays = checklist.filterValues { it }.keys
                    val checkedAmount = expenses.filter {
                        val day = Calendar.getInstance().apply { timeInMillis = it.dueDate }.get(Calendar.DAY_OF_MONTH)
                        checkedDays.contains(day)
                    }.sumOf { it.amount }
                    val remainingAmount = totalAmount - checkedAmount

                    Triple(days, totalAmount, remainingAmount)
                }

                _uiState.value = MonthlyExpenseListUiState.Success(days, totalAmount, remainingAmount)
            } catch (e: Exception) {
                _uiState.value = MonthlyExpenseListUiState.Error("Failed to load data: ${e.message}")
                emitError(R.string.error_load_data, e)
            }
        }
    }

    private fun buildFormattedExpenses(expenses: List<Expense>): CharSequence {
        if (expenses.isEmpty()) return ""

        val ssb = SpannableStringBuilder()
        val recurringSpan = getRecurringIconSpan()

        expenses.forEachIndexed { index, expense ->
            val expenseText = "${expense.description}: ${expense.amount.toCurrencyDisplay(appContext.resources)}"
            ssb.append(expenseText)

            if (expense.recurrenceType != RecurrenceType.NONE && recurringSpan != null) {
                val iconPlaceholderStart = ssb.length
                ssb.append("  ")
                val iconPlaceholderEnd = ssb.length
                ssb.setSpan(recurringSpan, iconPlaceholderStart, iconPlaceholderEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (index < expenses.size - 1) {
                ssb.append("\n")
            }
        }
        return ssb
    }

    private fun getRecurringIconSpan(): ImageSpan? {
        val drawable = ResourcesCompat.getDrawable(
            appContext.resources,
            R.drawable.ic_recurring,
            null
        ) ?: return null
        drawable.setTint(ContextCompat.getColor(appContext, R.color.text_on_container))
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        return ImageSpan(drawable, ImageSpan.ALIGN_BASELINE)
    }

    fun toggleDayChecked(day: Int, isChecked: Boolean) {
        viewModelScope.launch {
            val existing = repository.getChecklistItem(budgetId, day)
            if (existing != null) {
                val updated = existing.copy(isChecked = isChecked)
                repository.updateChecklistItem(updated)
                // Sync after update
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    SyncManager(appContext).uploadDailyChecklistItem(updated, userId)
                }
            } else {
                val newItem = DailyChecklist(budgetId = budgetId, dayOfMonth = day, isChecked = isChecked)
                repository.insertChecklistItem(newItem)
                // Sync after insert
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    SyncManager(appContext).uploadDailyChecklistItem(newItem, userId)
                }
            }

            val currentState = _uiState.value
            if (currentState is MonthlyExpenseListUiState.Success) {
                val updatedDays = currentState.days.map { dayExpenses ->
                    if (dayExpenses.day == day) dayExpenses.copy(isChecked = isChecked) else dayExpenses
                }
                val totalAmount = currentState.totalAmount
                val checkedAmount = updatedDays
                    .filter { it.isChecked }
                    .flatMap { it.expenses }
                    .sumOf { it.amount }
                val remainingAmount = totalAmount - checkedAmount
                _uiState.value = currentState.copy(
                    days = updatedDays,
                    remainingAmount = remainingAmount
                )
            }
        }
    }

    private fun getDaysInMonth(year: Int, month: Int): Int {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}