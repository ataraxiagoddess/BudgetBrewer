package com.ataraxiagoddess.budgetbrewer.data

import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Calendar

class BudgetRepository(private val db: AppDatabase) {

    // --- Budget ---
    suspend fun insertBudget(budget: Budget) = db.budgetDao().insert(budget)
    fun getBudget(month: Int, year: Int): Flow<Budget?> = db.budgetDao().getBudget(month, year)
    suspend fun getBudgetById(id: String): Budget? = db.budgetDao().getBudgetById(id)

    // --- Income ---
    suspend fun insertIncome(income: Income) = db.incomeDao().insert(income)
    suspend fun updateIncome(income: Income) = db.incomeDao().update(income)
    suspend fun deleteIncome(income: Income) = db.incomeDao().delete(income)
    fun getIncomesForBudget(budgetId: String): Flow<List<Income>> = db.incomeDao().getIncomesForBudget(budgetId)

    // --- Expense Categories ---
    suspend fun insertCategory(category: ExpenseCategory) = db.expenseCategoryDao().insert(category)
    suspend fun updateCategory(category: ExpenseCategory) = db.expenseCategoryDao().update(category)
    suspend fun deleteCategory(category: ExpenseCategory) = db.expenseCategoryDao().delete(category)
    fun getCategoriesForBudget(budgetId: String): Flow<List<ExpenseCategory>> = db.expenseCategoryDao().getCategoriesForBudget(budgetId)

    // --- Expenses ---
    suspend fun insertExpense(expense: Expense) = db.expenseDao().insert(expense)
    suspend fun updateExpense(expense: Expense) = db.expenseDao().update(expense)
    suspend fun deleteExpense(expense: Expense) = db.expenseDao().delete(expense)
    fun getExpensesForBudget(budgetId: String): Flow<List<Expense>> = db.expenseDao().getExpensesForBudget(budgetId)

    // --- Allocations ---
    suspend fun insertAllocation(allocation: Allocation) = db.allocationDao().insert(allocation)
    suspend fun updateAllocation(allocation: Allocation) = db.allocationDao().update(allocation)
    suspend fun deleteAllocation(allocation: Allocation) = db.allocationDao().delete(allocation)
    fun getAllocationForBudget(budgetId: String): Flow<Allocation?> = db.allocationDao().getAllocationForBudget(budgetId)

    // --- Daily Checklist ---
    fun getDailyChecklist(budgetId: String): Flow<List<DailyChecklist>> =
        db.dailyChecklistDao().getChecklistForBudget(budgetId)

    suspend fun getChecklistItem(budgetId: String, day: Int): DailyChecklist? =
        db.dailyChecklistDao().getChecklistItem(budgetId, day)

    suspend fun updateChecklistItem(item: DailyChecklist) {
        db.dailyChecklistDao().update(item)
    }

    suspend fun insertChecklistItem(item: DailyChecklist) {
        db.dailyChecklistDao().insert(item)
    }

    // --- Spending Entries ---
    fun getSpendingEntriesForBudget(budgetId: String): Flow<List<SpendingEntry>> =
        db.spendingEntryDao().getSpendingEntriesForBudget(budgetId)

    suspend fun insertSpendingEntry(entry: SpendingEntry) =
        db.spendingEntryDao().insert(entry)

    suspend fun updateSpendingEntry(entry: SpendingEntry) =
        db.spendingEntryDao().update(entry)

    suspend fun deleteSpendingEntry(entry: SpendingEntry) =
        db.spendingEntryDao().delete(entry)

    suspend fun getBudgetIdIfExists(month: Int, year: Int): String? {
        return db.budgetDao().getBudget(month, year).first()?.id
    }

    suspend fun getSpendingTotalForBudget(budgetId: String): Double {
        return db.spendingEntryDao().getSpendingEntriesForBudget(budgetId).first().sumOf { it.amount }
    }

    // --- Month Settings ---
    suspend fun getMonthEndAmount(budgetId: String): Double {
        // Fetch all necessary data
        val incomes = db.incomeDao().getIncomesForBudget(budgetId).first()
        val expenses = db.expenseDao().getExpensesForBudget(budgetId).first()
        val spendingEntries = db.spendingEntryDao().getSpendingEntriesForBudget(budgetId).first()
        val assignments = db.dailyIncomeAssignmentDao().getAssignmentsForBudget(budgetId).first()
        val budget = db.budgetDao().getBudgetById(budgetId) ?: return 0.0

        val monthStartAmount = db.monthSettingsDao().getSettingsForBudget(budgetId).first()?.monthStartAmount ?: 0.0
        val daysInMonth = getDaysInMonth(budget.year, budget.month)
        val firstDayOfWeek = getFirstDayOfWeek(budget.year, budget.month)

        val expensesByDay = expenses.groupBy { getDayOfMonth(it.dueDate) }
        val spendingByDay = spendingEntries.groupBy { getDayOfMonth(it.date) }
        val assignmentMap = assignments.groupBy { it.dayOfMonth }
        val incomesById = incomes.associateBy { it.id }

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
        weeks.forEach { weekDays ->
            val weekDaysActual = weekDays.filter { it.isCurrentMonth }
            runningTotal += weekDaysActual.sumOf { it.dayTotal }
        }
        return runningTotal
    }

    private fun getFirstDayOfWeek(year: Int, month: Int): Int {
        val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
        return cal.get(Calendar.DAY_OF_WEEK)
    }

    private data class CalendarDay(
        val dayOfMonth: Int,
        val isCurrentMonth: Boolean,
        val expenses: List<Expense>,
        val spendingEntries: List<SpendingEntry>,
        val assignedIncomes: List<Income>,
        val dayTotal: Double
    )

    fun getMonthSettings(budgetId: String): Flow<MonthSettings?> =
        db.monthSettingsDao().getSettingsForBudget(budgetId)

    // Inside ensureMonthSettings(budgetId: String)
    suspend fun ensureMonthSettings(budgetId: String) {
        Timber.d("ensureMonthSettings called for budget $budgetId")
        val budget = db.budgetDao().getBudgetById(budgetId) ?: return
        val existing = db.monthSettingsDao().getSettingsForBudget(budgetId).first()
        if (existing != null) return // already have settings

        // Compute month start amount = previous month's end amount
        val previousBudget = findPreviousBudget(budget.month, budget.year)
        val previousEnd = if (previousBudget != null) {
            ensureMonthSettings(previousBudget.id) // recursively ensure previous has settings
            getMonthEndAmount(previousBudget.id)   // <-- use new function
        } else {
            0.0 // no previous budget (first month ever)
        }

        // Insert the computed settings
        db.monthSettingsDao().insert(
            MonthSettings(
                budgetId = budgetId,
                monthStartAmount = previousEnd,
                monthStartOverridden = false
            )
        )
        Timber.d("Initialised MonthSettings for budget $budgetId: start=$previousEnd")
    }

    suspend fun insertOrUpdateMonthSettings(settings: MonthSettings) =
        db.monthSettingsDao().insert(settings)

    // Helper to get day of month
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

    // --- Daily Income Assignments ---
    fun getIncomeAssignmentsForBudget(budgetId: String): Flow<List<DailyIncomeAssignment>> =
        db.dailyIncomeAssignmentDao().getAssignmentsForBudget(budgetId)

    suspend fun getIncomeAssignment(budgetId: String, incomeId: String): DailyIncomeAssignment? =
        db.dailyIncomeAssignmentDao().getAssignmentByIncomeId(budgetId, incomeId)

    suspend fun assignIncomeToDay(budgetId: String, incomeId: String, day: Int) {
        val assignment = DailyIncomeAssignment(
            budgetId = budgetId,
            incomeId = incomeId,
            dayOfMonth = day
        )
        db.dailyIncomeAssignmentDao().insert(assignment)
    }

    suspend fun removeIncomeAssignment(budgetId: String, incomeId: String) {
        db.dailyIncomeAssignmentDao().deleteByIncomeId(budgetId, incomeId)
    }

    // --- Recurring expense propagation ---
    private fun calculateNextMonthlyDate(currentDate: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentDate
        calendar.add(Calendar.MONTH, 1)
        return calendar.timeInMillis
    }

    private fun calculateNextXDaysDate(currentDate: Long, interval: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentDate
        calendar.add(Calendar.DAY_OF_YEAR, interval)
        return calendar.timeInMillis
    }

    suspend fun findPreviousBudget(month: Int, year: Int): Budget? =
        db.budgetDao().findPreviousBudget(month, year)

    suspend fun propagateRecurringExpenses(fromBudgetId: String, toBudgetId: String) {
        val sourceCategories = db.expenseCategoryDao().getCategoriesForBudget(fromBudgetId).first()
        val sourceExpenses = db.expenseDao().getExpensesForBudget(fromBudgetId).first()
        val recurringExpenses = sourceExpenses.filter { it.recurrenceType != RecurrenceType.NONE }
        if (recurringExpenses.isEmpty()) return

        val targetCategories = db.expenseCategoryDao().getCategoriesForBudget(toBudgetId).first()
            .associateBy { it.name }

        val categoryMapping = mutableMapOf<String, String>() // sourceCategoryId -> targetCategoryId

        val sourceCategoryIdsWithRecurring = recurringExpenses.map { it.categoryId }.toSet()
        for (sourceCategoryId in sourceCategoryIdsWithRecurring) {
            val sourceCategory = sourceCategories.find { it.id == sourceCategoryId } ?: continue
            val targetCategory = targetCategories[sourceCategory.name]
            if (targetCategory != null) {
                categoryMapping[sourceCategoryId] = targetCategory.id
            } else {
                // Create new category in target budget with new UUID
                val newCategory = ExpenseCategory(
                    budgetId = toBudgetId,
                    name = sourceCategory.name,
                    color = sourceCategory.color,
                    displayOrder = sourceCategory.displayOrder,
                    createdAt = System.currentTimeMillis()
                )
                db.expenseCategoryDao().insert(newCategory)
                categoryMapping[sourceCategoryId] = newCategory.id
                Timber.d("Created category '${sourceCategory.name}' in target budget with id ${newCategory.id}")
            }
        }

        for (expense in recurringExpenses) {
            val targetCategoryId = categoryMapping[expense.categoryId] ?: continue
            when (expense.recurrenceType) {
                RecurrenceType.MONTHLY_SAME_DAY -> {
                    val newExpense = Expense(
                        categoryId = targetCategoryId,
                        description = expense.description,
                        amount = expense.amount,
                        dueDate = calculateNextMonthlyDate(expense.dueDate),
                        recurrenceType = expense.recurrenceType,
                        recurrenceInterval = expense.recurrenceInterval,
                        createdAt = System.currentTimeMillis(),
                        isActive = expense.isActive
                    )
                    db.expenseDao().insert(newExpense)
                    Timber.d("Inserted monthly recurring expense: ${expense.description}")
                }
                RecurrenceType.EVERY_X_DAYS -> {
                    val interval = expense.recurrenceInterval ?: continue
                    val nextDate = calculateNextXDaysDate(expense.dueDate, interval)
                    val newExpense = Expense(
                        categoryId = targetCategoryId,
                        description = expense.description,
                        amount = expense.amount,
                        dueDate = nextDate,
                        recurrenceType = expense.recurrenceType,
                        recurrenceInterval = expense.recurrenceInterval,
                        createdAt = System.currentTimeMillis(),
                        isActive = expense.isActive
                    )
                    db.expenseDao().insert(newExpense)
                    Timber.d("Inserted X-day recurring expense: ${expense.description}")
                }
                else -> {}
            }
        }
        Timber.d("Inserted ${recurringExpenses.size} recurring expenses")
    }

    suspend fun getOrCreateBudgetChain(targetMonth: Int, targetYear: Int): String {
        Timber.d("getOrCreateBudgetChain: target $targetMonth/$targetYear")
        val current = getBudget(targetMonth, targetYear).first()
        if (current != null) {
            Timber.d("Target budget already exists: id=${current.id}")
            ensureMonthSettings(current.id)
            return current.id
        }

        val previous = db.budgetDao().findPreviousBudget(targetMonth, targetYear)
        if (previous == null) {
            Timber.d("No previous budget found, creating target directly")
            val newBudget = Budget(month = targetMonth, year = targetYear)
            insertBudget(newBudget)
            ensureMonthSettings(newBudget.id)
            return newBudget.id
        }
        Timber.d("Previous budget found: ${previous.year}-${previous.month} id=${previous.id}")

        var fromBudgetId = previous.id
        val cal = Calendar.getInstance().apply {
            set(previous.year, previous.month - 1, 1)
            add(Calendar.MONTH, 1)
        }
        while (cal.get(Calendar.YEAR) < targetYear || (cal.get(Calendar.YEAR) == targetYear && cal.get(Calendar.MONTH) + 1 <= targetMonth)) {
            val month = cal.get(Calendar.MONTH) + 1
            val year = cal.get(Calendar.YEAR)
            Timber.d("Creating budget for $year-$month")
            val newBudget = Budget(month = month, year = year)
            insertBudget(newBudget)
            Timber.d("Propagating from $fromBudgetId to ${newBudget.id}")
            propagateRecurringExpenses(fromBudgetId, newBudget.id)
            ensureMonthSettings(newBudget.id)
            fromBudgetId = newBudget.id
            cal.add(Calendar.MONTH, 1)
        }
        Timber.d("Returning final budgetId: $fromBudgetId")
        return fromBudgetId
    }

    suspend fun updateAllIncomesCurrency(newCurrency: String) {
        db.incomeDao().updateAllCurrency(newCurrency)
    }
}