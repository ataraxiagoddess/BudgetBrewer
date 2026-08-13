/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.data

import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Calendar
import java.util.UUID

class BudgetRepository(
    private val db: AppDatabase,
) {
    private val budgetChainMutex = Mutex()
    private val propagateMutex = Mutex()

    // --- Budget ---
    suspend fun insertBudget(budget: Budget) = db.budgetDao().insert(budget)

    fun getBudget(
        month: Int,
        year: Int,
    ): Flow<Budget?> = db.budgetDao().getBudget(month, year)

    suspend fun getBudgetById(id: String): Budget? = db.budgetDao().getBudgetById(id)

    fun getPastBudgets(
        currentMonth: Int,
        currentYear: Int,
    ): Flow<List<Budget>> = db.budgetDao().getPastBudgets(currentMonth, currentYear)

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

    suspend fun deleteExpense(expense: Expense): String? {
        db.expenseDao().delete(expense)
        val category = db.expenseCategoryDao().getCategoryById(expense.categoryId) ?: return null
        val day = getDayOfMonth(expense.dueDate)
        val remainingExpenses =
            db
                .expenseDao()
                .getExpensesForBudget(category.budgetId)
                .first()
                .any { getDayOfMonth(it.dueDate) == day && it.id != expense.id }
        if (!remainingExpenses) {
            val checklistItem = db.dailyChecklistDao().getChecklistItem(category.budgetId, day)
            if (checklistItem != null) {
                db.dailyChecklistDao().delete(checklistItem)
                Timber.d("Deleted orphaned checklist item for day $day, budget ${category.budgetId}")
                return checklistItem.id
            }
        }
        return null
    }

    fun getExpensesForBudget(budgetId: String): Flow<List<Expense>> = db.expenseDao().getExpensesForBudget(budgetId)

    // --- Allocations ---
    suspend fun insertAllocation(allocation: Allocation) = db.allocationDao().insert(allocation)

    suspend fun updateAllocation(allocation: Allocation) = db.allocationDao().update(allocation)

    suspend fun deleteAllocation(allocation: Allocation) = db.allocationDao().delete(allocation)

    fun getAllocationForBudget(budgetId: String): Flow<Allocation?> = db.allocationDao().getAllocationForBudget(budgetId)

    // --- Daily Checklist ---
    fun getDailyChecklist(budgetId: String): Flow<List<DailyChecklist>> = db.dailyChecklistDao().getChecklistForBudget(budgetId)

    suspend fun getChecklistItem(
        budgetId: String,
        day: Int,
    ): DailyChecklist? = db.dailyChecklistDao().getChecklistItem(budgetId, day)

    suspend fun updateChecklistItem(item: DailyChecklist) {
        db.dailyChecklistDao().update(item)
    }

    suspend fun insertChecklistItem(item: DailyChecklist) {
        db.dailyChecklistDao().insert(item)
    }

    suspend fun deleteChecklistItem(item: DailyChecklist) {
        db.dailyChecklistDao().delete(item)
    }

    private suspend fun ensureChecklistItem(
        budgetId: String,
        dayOfMonth: Int,
    ) {
        val existing = db.dailyChecklistDao().getChecklistItem(budgetId, dayOfMonth)
        if (existing == null) {
            val item =
                DailyChecklist(
                    id = UUID.randomUUID().toString(),
                    budgetId = budgetId,
                    dayOfMonth = dayOfMonth,
                    isChecked = false,
                    updatedAt = System.currentTimeMillis(),
                )
            db.dailyChecklistDao().insert(item)
            Timber.d("Created checklist item for budget $budgetId, day $dayOfMonth")
        }
    }

    // --- Spending Entries ---
    fun getSpendingEntriesForBudget(budgetId: String): Flow<List<SpendingEntry>> =
        db.spendingEntryDao().getSpendingEntriesForBudget(budgetId)

    suspend fun insertSpendingEntry(entry: SpendingEntry) = db.spendingEntryDao().insert(entry)

    suspend fun updateSpendingEntry(entry: SpendingEntry) = db.spendingEntryDao().update(entry)

    suspend fun deleteSpendingEntry(entry: SpendingEntry) = db.spendingEntryDao().delete(entry)

    suspend fun getSpendingTotalForBudget(budgetId: String): Double =
        db.spendingEntryDao().getSpendingEntriesForBudget(budgetId).first().sumOf {
            it.amount
        }

    fun getSpendingTotalsByTag(budgetId: String): Flow<List<TagSpendingTotal>> = db.spendingEntryDao().getSpendingTotalsByTag(budgetId)

    suspend fun getSpendingTotalForMonth(
        month: Int,
        year: Int,
    ): Double {
        val budget = getBudget(month, year).first() ?: return 0.0
        return getSpendingTotalForBudget(budget.id)
    }

    // --- Savings Buckets ---
    fun getActiveSavingsBuckets(): Flow<List<SavingsBucket>> = db.savingsBucketDao().getNonArchivedBuckets()

    fun getArchivedSavingsBuckets(): Flow<List<SavingsBucket>> = db.savingsBucketDao().getArchivedBuckets()

    suspend fun insertSavingsBucket(bucket: SavingsBucket) = db.savingsBucketDao().insert(bucket)

    suspend fun distributeFunds(
        bucket: SavingsBucket,
        amount: Double,
    ): SavingsTransaction {
        val transaction =
            SavingsTransaction(
                bucket_id = bucket.id,
                amount = amount,
                date = System.currentTimeMillis(),
                type = if (amount >= 0) SavingsTransactionType.ALLOCATION else SavingsTransactionType.DEDUCTION,
            )
        db.savingsTransactionDao().insert(transaction)
        val total = db.savingsTransactionDao().getTotalForBucket(bucket.id)
        val updatedBucket = bucket.copy(current_amount = total, updated_at = System.currentTimeMillis())
        db.savingsBucketDao().update(updatedBucket)
        return transaction
    }

    suspend fun updateSavingsBucket(bucket: SavingsBucket) = db.savingsBucketDao().update(bucket)

    suspend fun deleteSavingsBucket(bucket: SavingsBucket) {
        db.savingsTransactionDao().deleteByBucketId(bucket.id)
        db.savingsBucketDao().delete(bucket)
    }

    suspend fun getTotalDistributedToBuckets(): Double = db.savingsTransactionDao().getAllTransactionsSync().sumOf { it.amount }

    suspend fun archiveBucket(bucket: SavingsBucket) {
        val updatedBucket =
            bucket.copy(
                is_archived = true,
                updated_at = System.currentTimeMillis(),
            )
        db.savingsBucketDao().update(updatedBucket)
    }

    suspend fun getSavingsBucketById(bucketId: String): SavingsBucket? = db.savingsBucketDao().getBucketById(bucketId)

    suspend fun restoreBucket(bucket: SavingsBucket) {
        val updatedBucket =
            bucket.copy(
                is_archived = false,
                updated_at = System.currentTimeMillis(),
            )
        db.savingsBucketDao().update(updatedBucket)
    }

    suspend fun editTransactionAmount(
        transaction: SavingsTransaction,
        newAmount: Double,
    ) {
        val updated =
            transaction.copy(
                amount = newAmount,
                updated_at = System.currentTimeMillis(),
            )
        db.savingsTransactionDao().updateTransaction(updated)
        val total = db.savingsTransactionDao().getTotalForBucket(transaction.bucket_id)
        val bucket = db.savingsBucketDao().getBucketById(transaction.bucket_id) ?: return
        db.savingsBucketDao().update(bucket.copy(current_amount = total, updated_at = System.currentTimeMillis()))
    }

    suspend fun deleteTransaction(transaction: SavingsTransaction) {
        db.savingsTransactionDao().deleteTransactionById(transaction.id)
        val total = db.savingsTransactionDao().getTotalForBucket(transaction.bucket_id)
        val bucket = db.savingsBucketDao().getBucketById(transaction.bucket_id) ?: return
        db.savingsBucketDao().update(bucket.copy(current_amount = total, updated_at = System.currentTimeMillis()))
    }

    fun getAvailableSavingsPool(): Flow<Double> =
        combine(
            db.allocationDao().getAllAllocations(),
            db.savingsTransactionDao().getAllTransactions(),
        ) { allocations, transactions ->
            val totalAllocated = allocations.sumOf { it.savingsAmount }
            val totalDistributed =
                transactions
                    .filter { it.type != SavingsTransactionType.WITHDRAWAL }
                    .sumOf { it.amount }
            totalAllocated - totalDistributed
        }

    // --- Savings Transactions ---
    fun getSavingsTransactionsByBucket(bucketId: String): Flow<List<SavingsTransaction>> =
        db.savingsTransactionDao().getTransactionsByBucket(bucketId)

    suspend fun getAllSavingsTransactions(): List<SavingsTransaction> = db.savingsTransactionDao().getAllTransactions().first()

    fun getAllSavingsTransactionsFlow(): Flow<List<SavingsTransaction>> = db.savingsTransactionDao().getAllTransactions()

    // --- Month Settings ---
    suspend fun getMonthEndAmount(budgetId: String): Double {
        val incomes = db.incomeDao().getIncomesForBudget(budgetId).first()
        val expenses = db.expenseDao().getExpensesForBudget(budgetId).first()
        val spendingEntries = db.spendingEntryDao().getSpendingEntriesForBudget(budgetId).first()
        val assignments = db.dailyIncomeAssignmentDao().getAssignmentsForBudget(budgetId).first()
        val budget = db.budgetDao().getBudgetById(budgetId) ?: return 0.0

        val monthStartAmount =
            db
                .monthSettingsDao()
                .getSettingsForBudget(budgetId)
                .first()
                ?.monthStartAmount ?: 0.0
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
                    dayTotal = dayTotal,
                ),
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

    private fun getFirstDayOfWeek(
        year: Int,
        month: Int,
    ): Int {
        val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
        return cal.get(Calendar.DAY_OF_WEEK)
    }

    private data class CalendarDay(
        val dayOfMonth: Int,
        val isCurrentMonth: Boolean,
        val expenses: List<Expense>,
        val spendingEntries: List<SpendingEntry>,
        val assignedIncomes: List<Income>,
        val dayTotal: Double,
    )

    fun getMonthSettings(budgetId: String): Flow<MonthSettings?> = db.monthSettingsDao().getSettingsForBudget(budgetId)

    suspend fun ensureMonthSettings(budgetId: String) {
        Timber.d("ensureMonthSettings called for budget $budgetId")
        val budget = db.budgetDao().getBudgetById(budgetId) ?: return
        val existing = db.monthSettingsDao().getSettingsForBudget(budgetId).first()
        if (existing != null) return

        val previousBudget = findPreviousBudget(budget.month, budget.year)
        val previousEnd =
            if (previousBudget != null) {
                ensureMonthSettings(previousBudget.id)
                getMonthEndAmount(previousBudget.id)
            } else {
                0.0
            }

        db.monthSettingsDao().insert(
            MonthSettings(
                budgetId = budgetId,
                monthStartAmount = previousEnd,
                monthStartOverridden = false,
            ),
        )
        Timber.d("Initialised MonthSettings for budget $budgetId: start=$previousEnd")
    }

    suspend fun insertOrUpdateMonthSettings(settings: MonthSettings) = db.monthSettingsDao().insert(settings)

    private fun getDayOfMonth(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    fun generateOccurrenceDatesInMonth(
        baseDate: Long,
        intervalDays: Int,
        targetMonth: Int,
        targetYear: Int,
    ): List<Long> {
        val dates = mutableListOf<Long>()
        val targetStart =
            Calendar
                .getInstance()
                .apply {
                    set(targetYear, targetMonth - 1, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
        val targetEnd =
            Calendar
                .getInstance()
                .apply {
                    set(targetYear, targetMonth - 1, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.MONTH, 1)
                }.timeInMillis

        val cal =
            Calendar.getInstance().apply {
                timeInMillis = baseDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        if (cal.timeInMillis >= targetEnd) return emptyList()

        while (cal.timeInMillis < targetStart) {
            cal.add(Calendar.DAY_OF_MONTH, intervalDays)
        }

        if (cal.timeInMillis >= targetEnd) return emptyList()

        while (cal.timeInMillis < targetEnd) {
            dates.add(cal.timeInMillis)
            cal.add(Calendar.DAY_OF_MONTH, intervalDays)
        }
        return dates
    }

    private fun getDaysInMonth(
        year: Int,
        month: Int,
    ): Int =
        when (month) {
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

    // --- Daily Income Assignments ---
    fun getIncomeAssignmentsForBudget(budgetId: String): Flow<List<DailyIncomeAssignment>> =
        db.dailyIncomeAssignmentDao().getAssignmentsForBudget(budgetId)

    suspend fun getIncomeAssignment(
        budgetId: String,
        incomeId: String,
    ): DailyIncomeAssignment? = db.dailyIncomeAssignmentDao().getAssignmentByIncomeId(budgetId, incomeId)

    suspend fun assignIncomeToDay(
        budgetId: String,
        incomeId: String,
        day: Int,
    ) {
        val assignment =
            DailyIncomeAssignment(
                budgetId = budgetId,
                incomeId = incomeId,
                dayOfMonth = day,
            )
        db.dailyIncomeAssignmentDao().insert(assignment)
    }

    suspend fun removeIncomeAssignment(
        budgetId: String,
        incomeId: String,
    ) {
        db.dailyIncomeAssignmentDao().deleteByIncomeId(budgetId, incomeId)
    }

    suspend fun findPreviousBudget(
        month: Int,
        year: Int,
    ): Budget? = db.budgetDao().findPreviousBudget(month, year)

    // ---------- CORRECTED propagateRecurringExpenses ----------
    suspend fun propagateRecurringExpenses(
        fromBudgetId: String,
        toBudgetId: String,
    ) = propagateMutex.withLock {
        // 1. Get source and target data
        val sourceCategories = db.expenseCategoryDao().getCategoriesForBudget(fromBudgetId).first()
        val sourceExpenses = db.expenseDao().getExpensesForBudget(fromBudgetId).first()
        val targetCategories = db.expenseCategoryDao().getCategoriesForBudget(toBudgetId).first()
        val targetExpenses = db.expenseDao().getExpensesForBudget(toBudgetId).first()

        // 2. Build category mapping (sourceCategoryId -> targetCategoryId)
        val allRecurring = sourceExpenses.filter { it.recurrenceType != RecurrenceType.NONE }
        val categoryMap = mutableMapOf<String, String>()
        val sourceCategoryIds = allRecurring.map { it.categoryId }.toSet()
        for (sourceCategoryId in sourceCategoryIds) {
            val sourceCategory = sourceCategories.find { it.id == sourceCategoryId } ?: continue
            val targetCategory = targetCategories.find { it.name == sourceCategory.name }
            if (targetCategory != null) {
                categoryMap[sourceCategoryId] = targetCategory.id
            } else {
                val newCategory =
                    ExpenseCategory(
                        budgetId = toBudgetId,
                        name = sourceCategory.name,
                        color = sourceCategory.color,
                        displayOrder = sourceCategory.displayOrder,
                        createdAt = System.currentTimeMillis(),
                    )
                db.expenseCategoryDao().insert(newCategory)
                categoryMap[sourceCategoryId] = newCategory.id
            }
        }

        // 3. Handle MONTHLY_SAME_DAY (1-to-1 propagation)
        val sourceMonthly = sourceExpenses.filter { it.recurrenceType == RecurrenceType.MONTHLY_SAME_DAY }
        val targetMonthly = targetExpenses.filter { it.recurrenceType == RecurrenceType.MONTHLY_SAME_DAY }

        val sourceMonthlyMap = sourceMonthly.associateBy { it.id }
        val targetMonthlyMap = targetMonthly.filter { it.sourceExpenseId != null }.associateBy { it.sourceExpenseId!! }

        val monthlyKeysToInsert = sourceMonthlyMap.keys - targetMonthlyMap.keys
        val monthlyKeysToUpdate = sourceMonthlyMap.keys.intersect(targetMonthlyMap.keys)
        val monthlyKeysToDelete = targetMonthlyMap.keys - sourceMonthlyMap.keys

        suspend fun shiftMonthlyDueDate(
            sourceDueDate: Long,
            sourceBudgetId: String,
            targetBudgetId: String,
        ): Long {
            val sourceBudget = db.budgetDao().getBudgetById(sourceBudgetId) ?: return sourceDueDate
            val targetBudget = db.budgetDao().getBudgetById(targetBudgetId) ?: return sourceDueDate
            val cal = Calendar.getInstance()
            cal.timeInMillis = sourceDueDate
            val monthDiff = (targetBudget.year - sourceBudget.year) * 12 + (targetBudget.month - sourceBudget.month)
            cal.add(Calendar.MONTH, monthDiff)
            return cal.timeInMillis
        }

        // Insert new monthly expenses
        for (key in monthlyKeysToInsert) {
            val sourceExpense = sourceMonthlyMap[key]!!
            val targetCategoryId = categoryMap[sourceExpense.categoryId] ?: continue
            val newDueDate = shiftMonthlyDueDate(sourceExpense.dueDate, fromBudgetId, toBudgetId)
            val newExpense =
                Expense(
                    categoryId = targetCategoryId,
                    description = sourceExpense.description,
                    amount = sourceExpense.amount,
                    dueDate = newDueDate,
                    recurrenceType = sourceExpense.recurrenceType,
                    recurrenceInterval = sourceExpense.recurrenceInterval,
                    sourceExpenseId = sourceExpense.id,
                    createdAt = System.currentTimeMillis(),
                    isActive = sourceExpense.isActive,
                )
            db.expenseDao().insert(newExpense)
            ensureChecklistItem(toBudgetId, getDayOfMonth(newDueDate))
            Timber.d("Inserted monthly recurring expense: ${sourceExpense.description} to budget $toBudgetId")
        }

        // Update existing monthly expenses
        for (key in monthlyKeysToUpdate) {
            val sourceExpense = sourceMonthlyMap[key]!!
            val targetExpense = targetMonthlyMap[key]!!
            if (targetExpense.isOverridden) {
                Timber.d("Skipping update for overridden expense: ${targetExpense.description}")
                continue
            }
            val oldDueDate = targetExpense.dueDate
            val newDueDate = shiftMonthlyDueDate(sourceExpense.dueDate, fromBudgetId, toBudgetId)
            if (targetExpense.amount != sourceExpense.amount ||
                targetExpense.description != sourceExpense.description ||
                targetExpense.dueDate != newDueDate ||
                targetExpense.recurrenceType != sourceExpense.recurrenceType ||
                targetExpense.recurrenceInterval != sourceExpense.recurrenceInterval
            ) {
                val updated =
                    targetExpense.copy(
                        amount = sourceExpense.amount,
                        description = sourceExpense.description,
                        dueDate = newDueDate,
                        recurrenceType = sourceExpense.recurrenceType,
                        recurrenceInterval = sourceExpense.recurrenceInterval,
                        updatedAt = System.currentTimeMillis(),
                    )
                db.expenseDao().update(updated)

                // Clean up old checklist item if due date changed
                if (oldDueDate != newDueDate) {
                    val oldDay = getDayOfMonth(oldDueDate)
                    val remainingOnOldDay =
                        db
                            .expenseDao()
                            .getExpensesForBudget(toBudgetId)
                            .first()
                            .any { getDayOfMonth(it.dueDate) == oldDay && it.id != targetExpense.id }
                    if (!remainingOnOldDay) {
                        db.dailyChecklistDao().getChecklistItem(toBudgetId, oldDay)?.let {
                            db.dailyChecklistDao().delete(it)
                        }
                    }
                }
                ensureChecklistItem(toBudgetId, getDayOfMonth(newDueDate))
                Timber.d("Updated monthly recurring expense: ${sourceExpense.description} in budget $toBudgetId")
            }
        }

        // Delete monthly expenses that no longer exist in source
        for (key in monthlyKeysToDelete) {
            val targetExpense = targetMonthlyMap[key]!!
            db.expenseDao().delete(targetExpense)
            val day = getDayOfMonth(targetExpense.dueDate)
            val remaining =
                db
                    .expenseDao()
                    .getExpensesForBudget(toBudgetId)
                    .first()
                    .any { getDayOfMonth(it.dueDate) == day && it.id != targetExpense.id }
            if (!remaining) {
                db.dailyChecklistDao().getChecklistItem(toBudgetId, day)?.let {
                    db.dailyChecklistDao().delete(it)
                }
            }
            Timber.d("Deleted monthly recurring expense: ${targetExpense.description} from budget $toBudgetId")
        }

        // 4. Handle EVERY_X_DAYS (master-children model)
        val sourceEveryX = sourceExpenses.filter { it.recurrenceType == RecurrenceType.EVERY_X_DAYS }
        val masterIds = sourceEveryX.map { it.sourceExpenseId ?: it.id }.toSet()
        val targetBudget = db.budgetDao().getBudgetById(toBudgetId) ?: return@withLock

        for (masterId in masterIds) {
            val lineageExpenses =
                sourceEveryX.filter {
                    (it.sourceExpenseId ?: it.id) == masterId
                }
            if (lineageExpenses.isEmpty()) continue

            val patternSource = lineageExpenses.maxByOrNull { it.updatedAt } ?: continue
            val intervalDays = patternSource.recurrenceInterval ?: continue

            val targetCategoryId = categoryMap[patternSource.categoryId] ?: continue

            val targetDates =
                generateOccurrenceDatesInMonth(
                    baseDate = patternSource.dueDate,
                    intervalDays = intervalDays,
                    targetMonth = targetBudget.month,
                    targetYear = targetBudget.year,
                )

            val existingChildren =
                targetExpenses.filter {
                    it.sourceExpenseId == masterId && it.recurrenceType == RecurrenceType.EVERY_X_DAYS
                }
            val existingByDate = existingChildren.associateBy { it.dueDate }
            val targetDateSet = targetDates.toSet()

            // Insert or update children
            for (date in targetDates) {
                val existing = existingByDate[date]
                if (existing != null) {
                    if (existing.amount != patternSource.amount ||
                        existing.description != patternSource.description ||
                        existing.recurrenceInterval != patternSource.recurrenceInterval
                    ) {
                        val updated =
                            existing.copy(
                                amount = patternSource.amount,
                                description = patternSource.description,
                                recurrenceInterval = patternSource.recurrenceInterval,
                                updatedAt = patternSource.updatedAt,
                            )
                        db.expenseDao().update(updated)
                    }
                } else {
                    val newChild =
                        Expense(
                            categoryId = targetCategoryId,
                            description = patternSource.description,
                            amount = patternSource.amount,
                            dueDate = date,
                            recurrenceType = patternSource.recurrenceType,
                            recurrenceInterval = patternSource.recurrenceInterval,
                            sourceExpenseId = masterId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = patternSource.updatedAt,
                            isActive = patternSource.isActive,
                        )
                    db.expenseDao().insert(newChild)
                    ensureChecklistItem(toBudgetId, getDayOfMonth(date))
                    Timber.d("Inserted EVERY_X_DAYS child: ${patternSource.description} on $date")
                }
            }

            // Delete children that no longer fit the pattern
            for (existing in existingChildren) {
                if (existing.dueDate !in targetDateSet) {
                    db.expenseDao().delete(existing)
                    val day = getDayOfMonth(existing.dueDate)
                    val remaining =
                        db
                            .expenseDao()
                            .getExpensesForBudget(toBudgetId)
                            .first()
                            .any { getDayOfMonth(it.dueDate) == day && it.id != existing.id }
                    if (!remaining) {
                        db.dailyChecklistDao().getChecklistItem(toBudgetId, day)?.let {
                            db.dailyChecklistDao().delete(it)
                        }
                    }
                    Timber.d("Deleted EVERY_X_DAYS child: ${existing.description} from budget $toBudgetId")
                }
            }
        }
    }

    suspend fun getOrCreateBudgetChain(
        targetMonth: Int,
        targetYear: Int,
    ): Pair<String, Boolean> {
        return budgetChainMutex.withLock {
            Timber.d("getOrCreateBudgetChain: target $targetMonth/$targetYear")
            var created = false

            val current = getBudget(targetMonth, targetYear).first()
            if (current != null) {
                Timber.d("Target budget already exists: id=${current.id}")
                ensureMonthSettings(current.id)
                return@withLock Pair(current.id, false)
            }

            val previous = db.budgetDao().findPreviousBudget(targetMonth, targetYear)
            if (previous == null) {
                Timber.d("No previous budget found, creating target directly")
                val newBudget = Budget(month = targetMonth, year = targetYear)
                insertBudget(newBudget)
                ensureMonthSettings(newBudget.id)
                return@withLock Pair(newBudget.id, true)
            }

            Timber.d("Previous budget found: ${previous.year}-${previous.month} id=${previous.id}")

            var fromBudgetId = previous.id
            val cal =
                Calendar.getInstance().apply {
                    set(previous.year, previous.month - 1, 1)
                    add(Calendar.MONTH, 1)
                }

            while (cal.get(Calendar.YEAR) < targetYear ||
                (cal.get(Calendar.YEAR) == targetYear && cal.get(Calendar.MONTH) + 1 <= targetMonth)
            ) {
                val month = cal.get(Calendar.MONTH) + 1
                val year = cal.get(Calendar.YEAR)

                Timber.d("Creating budget for $year-$month")
                val newBudget = Budget(month = month, year = year)
                insertBudget(newBudget)

                if (month == targetMonth && year == targetYear) {
                    created = true
                }

                Timber.d("Propagating from $fromBudgetId to ${newBudget.id}")
                propagateRecurringExpenses(fromBudgetId, newBudget.id)
                ensureMonthSettings(newBudget.id)

                fromBudgetId = newBudget.id
                cal.add(Calendar.MONTH, 1)
            }

            Timber.d("Returning final budgetId: $fromBudgetId (created=$created)")
            return@withLock Pair(fromBudgetId, created)
        }
    }

    suspend fun updateAllIncomesCurrency(newCurrency: String) {
        db.incomeDao().updateAllCurrency(newCurrency)
    }
}
