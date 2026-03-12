// file: ui/calendar/MonthlyCalendarViewModel.kt

package com.ataraxiagoddess.budgetbrewer.ui.calendar

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.Expense
import com.ataraxiagoddess.budgetbrewer.data.Income
import com.ataraxiagoddess.budgetbrewer.data.MonthSettings
import com.ataraxiagoddess.budgetbrewer.data.SpendingEntry
import com.ataraxiagoddess.budgetbrewer.data.SyncManager
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseViewModel
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar

class MonthlyCalendarViewModel(
    private val repository: BudgetRepository,
    private val savedStateHandle: SavedStateHandle,
    private val appContext: Context
) : BaseViewModel() {

    private var budgetId: String = savedStateHandle.get<String>("budgetId") ?: ""
    private var currentMonth: Month = Month.current()

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    data class CalendarDay(
        val dayOfMonth: Int,
        val isCurrentMonth: Boolean,
        val expenses: List<Expense>,
        val spendingEntries: List<SpendingEntry>,
        val assignedIncomes: List<Income>,
        val dayTotal: Double
    )

    data class WeekEndTotal(
        val weekNumber: Int,
        val total: Double
        // isOverridden removed
    )

    data class CalendarData(
        val month: Month,
        val monthStartAmount: Double,
        val monthStartOverridden: Boolean,
        val weeks: List<List<CalendarDay>>,
        val weekEndTotals: List<WeekEndTotal>,
        val monthEndAmount: Double,
        val unassignedIncomes: List<Income>
    )

    sealed class CalendarUiState {
        object Loading : CalendarUiState()
        data class Success(val data: CalendarData) : CalendarUiState()
        data class Error(val message: String) : CalendarUiState()
    }

    fun updateMonth(month: Month) {
        viewModelScope.launch {
            currentMonth = month
            val newBudgetId = repository.getOrCreateBudgetChain(month.month, month.year)
            budgetId = newBudgetId
            savedStateHandle["budgetId"] = newBudgetId
            loadData()
        }
    }

    fun updateMonthStartAmount(amount: Double) {
        viewModelScope.launch {
            val settings = repository.getMonthSettings(budgetId).first()
            val updatedSettings = (settings ?: MonthSettings(budgetId = budgetId, monthStartAmount = amount))
                .copy(
                    monthStartAmount = amount,
                    monthStartOverridden = true
                )
            repository.insertOrUpdateMonthSettings(updatedSettings)
            // Sync after update
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadMonthSetting(updatedSettings, userId)
            }
            loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = CalendarUiState.Loading
            try {
                val month = currentMonth
                val incomes = repository.getIncomesForBudget(budgetId).first()
                val expenses = repository.getExpensesForBudget(budgetId).first()
                val spendingEntries = repository.getSpendingEntriesForBudget(budgetId).first()
                val assignments = repository.getIncomeAssignmentsForBudget(budgetId).first()
                val settings = repository.getMonthSettings(budgetId).first()

                val previousMonthEnd = getPreviousMonthEndAmount(month)
                val (monthStartAmount, monthStartOverridden) = if (settings?.monthStartOverridden == true) {
                    settings.monthStartAmount to true
                } else {
                    previousMonthEnd to false
                }

                if (!monthStartOverridden) {
                    val newSettings = MonthSettings(
                        budgetId = budgetId,
                        monthStartAmount = monthStartAmount,
                        monthStartOverridden = false
                    )
                    repository.insertOrUpdateMonthSettings(newSettings)
                }

                val expensesByDay = expenses.groupBy { getDayOfMonth(it.dueDate) }
                val spendingByDay = spendingEntries.groupBy { getDayOfMonth(it.date) }

                val assignmentMap = assignments.groupBy { it.dayOfMonth }
                val incomesById = incomes.associateBy { it.id }
                val assignedIncomeIds = assignments.map { it.incomeId }.toSet()
                val unassignedIncomes = incomes.filter { it.id !in assignedIncomeIds }

                val daysInMonth = getDaysInMonth(month.year, month.month)
                val firstDayOfWeek = getFirstDayOfWeek(month.year, month.month)

                val allDays = mutableListOf<CalendarDay>()
                repeat(firstDayOfWeek - 1) {
                    allDays.add(CalendarDay(0, false, emptyList(), emptyList(), emptyList(), 0.0))
                }
                for (day in 1..daysInMonth) {
                    val dayExpenses = expensesByDay[day] ?: emptyList()
                    val daySpending = spendingByDay[day] ?: emptyList()
                    val assigned = assignmentMap[day]?.mapNotNull { incomesById[it.incomeId] } ?: emptyList()
                    val dayIncomeTotal = assigned.sumOf { it.amount }
                    val dayExpenseTotal = dayExpenses.sumOf { it.amount } + daySpending.sumOf { it.amount }
                    val dayTotal = dayIncomeTotal - dayExpenseTotal
                    allDays.add(
                        CalendarDay(
                            dayOfMonth = day,
                            isCurrentMonth = true,
                            expenses = dayExpenses,
                            spendingEntries = daySpending,
                            assignedIncomes = assigned,
                            dayTotal = dayTotal
                        )
                    )
                }
                while (allDays.size % 7 != 0) {
                    allDays.add(CalendarDay(0, false, emptyList(), emptyList(), emptyList(), 0.0))
                }

                val weeks = allDays.chunked(7)

                var runningTotal = monthStartAmount
                val weekEndTotals = mutableListOf<WeekEndTotal>()
                weeks.forEachIndexed { weekIndex, weekDays ->
                    val weekDaysActual = weekDays.filter { it.isCurrentMonth }
                    runningTotal += weekDaysActual.sumOf { it.dayTotal }
                    weekEndTotals.add(
                        WeekEndTotal(
                            weekNumber = weekIndex + 1,
                            total = runningTotal
                        )
                    )
                }
                val monthEndAmount = runningTotal

                _uiState.value = CalendarUiState.Success(
                    CalendarData(
                        month = month,
                        monthStartAmount = monthStartAmount,
                        monthStartOverridden = monthStartOverridden,
                        weeks = weeks,
                        weekEndTotals = weekEndTotals,
                        monthEndAmount = monthEndAmount,
                        unassignedIncomes = unassignedIncomes
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "loadData() failed")
                _uiState.value = CalendarUiState.Error("Failed to load calendar: ${e.message}")
                emitError(R.string.error_load_data, e)
            }
        }
    }

    // Helper functions (unchanged)
    private fun getDayOfMonth(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun getDaysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1 -> 31
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            3 -> 31
            4 -> 30
            5 -> 31
            6 -> 30
            7 -> 31
            8 -> 31
            9 -> 30
            10 -> 31
            11 -> 30
            12 -> 31
            else -> throw IllegalArgumentException("Invalid month: $month")
        }
    }

    private fun getFirstDayOfWeek(year: Int, month: Int): Int {
        val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
        return cal.get(Calendar.DAY_OF_WEEK)
    }

    private suspend fun getPreviousMonthEndAmount(current: Month): Double {
        val previousBudget = repository.findPreviousBudget(current.month, current.year) ?: return 0.0
        repository.ensureMonthSettings(previousBudget.id)
        return repository.getMonthEndAmount(previousBudget.id)
    }

    fun assignIncomeToDay(day: Int, income: Income) {
        viewModelScope.launch {
            repository.assignIncomeToDay(budgetId, income.id, day)
            val assignment = repository.getIncomeAssignment(budgetId, income.id)
            if (assignment != null) {
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    SyncManager(appContext).uploadDailyIncomeAssignment(assignment, userId)
                }
            }
            loadData()
        }
    }

    fun removeIncomeFromDay(income: Income) {
        viewModelScope.launch {
            val assignments = repository.getIncomeAssignmentsForBudget(budgetId).first()
            val assignment = assignments.find { it.incomeId == income.id }
            assignment?.let {
                repository.removeIncomeAssignment(budgetId, income.id)
                val userId = AuthManager.getUserId(appContext)
                if (userId != null) {
                    SyncManager(appContext).deleteDailyIncomeAssignment(it.id, userId)
                }
                loadData()
            }
        }
    }
}