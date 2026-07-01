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

class BudgetRepository(private val db: AppDatabase) {

    private val budgetChainMutex = Mutex()

    // --- Budget ---
    suspend fun insertBudget(budget: Budget) = db.budgetDao().insert(budget)
    fun getBudget(month: Int, year: Int): Flow<Budget?> = db.budgetDao().getBudget(month, year)
    suspend fun getBudgetById(id: String): Budget? = db.budgetDao().getBudgetById(id)
    fun getPastBudgets(currentMonth: Int, currentYear: Int): Flow<List<Budget>> =
        db.budgetDao().getPastBudgets(currentMonth, currentYear)

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
        // 1. Delete the expense
        db.expenseDao().delete(expense)

        // 2. Get the category to find budgetId
        val category = db.expenseCategoryDao().getCategoryById(expense.categoryId) ?: return null
        val day = getDayOfMonth(expense.dueDate)

        // 3. Check if any other expenses exist for this budget on this day
        val remainingExpenses = db.expenseDao().getExpensesForBudget(category.budgetId).first()
            .any { getDayOfMonth(it.dueDate) == day && it.id != expense.id }

        // 4. If no other expenses on this day, delete the checklist item
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

    private suspend fun ensureChecklistItem(budgetId: String, dayOfMonth: Int) {
        val existing = db.dailyChecklistDao().getChecklistItem(budgetId, dayOfMonth)
        if (existing == null) {
            val item = DailyChecklist(
                id = UUID.randomUUID().toString(),
                budgetId = budgetId,
                dayOfMonth = dayOfMonth,
                isChecked = false,
                updatedAt = System.currentTimeMillis()
            )
            db.dailyChecklistDao().insert(item)
            Timber.d("Created checklist item for budget $budgetId, day $dayOfMonth")
        }
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

    suspend fun getSpendingTotalForBudget(budgetId: String): Double {
        return db.spendingEntryDao().getSpendingEntriesForBudget(budgetId).first().sumOf { it.amount }
    }

    fun getSpendingTotalsByTag(budgetId: String): Flow<List<TagSpendingTotal>> =
        db.spendingEntryDao().getSpendingTotalsByTag(budgetId)

    suspend fun getSpendingTotalForMonth(month: Int, year: Int): Double {
        val budget = getBudget(month, year).first() ?: return 0.0
        return getSpendingTotalForBudget(budget.id)
    }

    // --- Savings Buckets ---
    fun getActiveSavingsBuckets(): Flow<List<SavingsBucket>> =
        db.savingsBucketDao().getNonArchivedBuckets()

    fun getArchivedSavingsBuckets(): Flow<List<SavingsBucket>> =
        db.savingsBucketDao().getArchivedBuckets()

    suspend fun insertSavingsBucket(bucket: SavingsBucket) =
        db.savingsBucketDao().insert(bucket)

    suspend fun distributeFunds(bucket: SavingsBucket, amount: Double): SavingsTransaction {
        val transaction = SavingsTransaction(
            bucket_id = bucket.id,
            amount = amount,
            date = System.currentTimeMillis(),
            type = if (amount >= 0) SavingsTransactionType.ALLOCATION else SavingsTransactionType.DEDUCTION
        )
        db.savingsTransactionDao().insert(transaction)

        // Update current_amount on the bucket
        val total = db.savingsTransactionDao().getTotalForBucket(bucket.id)
        val updatedBucket = bucket.copy(current_amount = total, updated_at = System.currentTimeMillis())
        db.savingsBucketDao().update(updatedBucket)
        return transaction
    }

    suspend fun updateSavingsBucket(bucket: SavingsBucket) =
        db.savingsBucketDao().update(bucket)

    suspend fun deleteSavingsBucket(bucket: SavingsBucket) {
        // Manual cascade delete of transactions first
        db.savingsTransactionDao().deleteByBucketId(bucket.id)
        db.savingsBucketDao().delete(bucket)
    }

    /**
     * Total amount distributed to all savings buckets.
     * (This is the sum of all savings transactions – allocations are positive, deductions negative.)
     */
    suspend fun getTotalDistributedToBuckets(): Double {
        return db.savingsTransactionDao().getAllTransactionsSync().sumOf { it.amount }
    }

    suspend fun archiveBucket(bucket: SavingsBucket) {
        val updatedBucket = bucket.copy(
            is_archived = true,
            updated_at = System.currentTimeMillis()
        )
        db.savingsBucketDao().update(updatedBucket)
    }

    suspend fun getSavingsBucketById(bucketId: String): SavingsBucket? =
        db.savingsBucketDao().getBucketById(bucketId)

    suspend fun restoreBucket(bucket: SavingsBucket) {
        val updatedBucket = bucket.copy(
            is_archived = false,
            updated_at = System.currentTimeMillis()
        )
        db.savingsBucketDao().update(updatedBucket)
    }

    suspend fun editTransactionAmount(transaction: SavingsTransaction, newAmount: Double) {
        val updated = transaction.copy(
            amount = newAmount,
            updated_at = System.currentTimeMillis()
            )
        db.savingsTransactionDao().updateTransaction(updated)

        // Recalculate current_amount for the bucket
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

    fun getAvailableSavingsPool(): Flow<Double> {
        return combine(
            db.allocationDao().getAllAllocations(),
            db.savingsTransactionDao().getAllTransactions()   // we need a Flow version
        ) { allocations, transactions ->
            val totalAllocated = allocations.sumOf { it.savingsAmount }
            val totalDistributed = transactions
                .filter { it.type != SavingsTransactionType.WITHDRAWAL }
                .sumOf { it.amount }
            totalAllocated - totalDistributed
        }
    }

    // --- Savings Transactions ---
    fun getSavingsTransactionsByBucket(bucketId: String): Flow<List<SavingsTransaction>> =
        db.savingsTransactionDao().getTransactionsByBucket(bucketId)

    suspend fun getAllSavingsTransactions(): List<SavingsTransaction> {
        return db.savingsTransactionDao().getAllTransactions().first()
    }

    fun getAllSavingsTransactionsFlow(): Flow<List<SavingsTransaction>> {
        return db.savingsTransactionDao().getAllTransactions()
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

    suspend fun findPreviousBudget(month: Int, year: Int): Budget? =
        db.budgetDao().findPreviousBudget(month, year)

    private val propagateMutex = Mutex()
    suspend fun propagateRecurringExpenses(fromBudgetId: String, toBudgetId: String) = propagateMutex.withLock {
        // 1. Get source categories and expenses
        val sourceCategories = db.expenseCategoryDao().getCategoriesForBudget(fromBudgetId).first()
        val sourceExpenses = db.expenseDao().getExpensesForBudget(fromBudgetId).first()
        val sourceRecurring = sourceExpenses.filter { it.recurrenceType != RecurrenceType.NONE }

        // 2. Get target categories and expenses
        val targetCategories = db.expenseCategoryDao().getCategoriesForBudget(toBudgetId).first()
        val targetExpenses = db.expenseDao().getExpensesForBudget(toBudgetId).first()
        val targetRecurring = targetExpenses.filter { it.recurrenceType != RecurrenceType.NONE }

        // 3. Build category mapping (sourceCategoryId -> targetCategoryId)
        val categoryMap = mutableMapOf<String, String>()
        val sourceCategoryIds = sourceRecurring.map { it.categoryId }.toSet()
        for (sourceCategoryId in sourceCategoryIds) {
            val sourceCategory = sourceCategories.find { it.id == sourceCategoryId } ?: continue
            val targetCategory = targetCategories.find { it.name == sourceCategory.name }
            if (targetCategory != null) {
                categoryMap[sourceCategoryId] = targetCategory.id
            } else {
                // Create new category in target
                val newCategory = ExpenseCategory(
                    budgetId = toBudgetId,
                    name = sourceCategory.name,
                    color = sourceCategory.color,
                    displayOrder = sourceCategory.displayOrder,
                    createdAt = System.currentTimeMillis()
                )
                db.expenseCategoryDao().insert(newCategory)
                categoryMap[sourceCategoryId] = newCategory.id
            }
        }

        // 4. Build source map by id (source expenses have sourceExpenseId = null or their own id)
        val sourceMap = sourceRecurring.associateBy { it.id }

        // 5. Build target map by sourceExpenseId (link back to source)
        val targetMap = targetRecurring
            .filter { it.sourceExpenseId != null }
            .associateBy { it.sourceExpenseId!! }

        // 6. Determine which expenses to insert, update, delete
        val keysToInsert = sourceMap.keys - targetMap.keys
        val keysToUpdate = sourceMap.keys.intersect(targetMap.keys)
        val keysToDelete = targetMap.keys - sourceMap.keys

        // 8. Suspend function to shift due date based on budget month difference and recurrence type
        suspend fun shiftDueDate(
            sourceDueDate: Long,
            sourceBudgetId: String,
            targetBudgetId: String,
            recurrenceType: RecurrenceType,
            recurrenceInterval: Int?
        ): Long {
            val sourceBudget = db.budgetDao().getBudgetById(sourceBudgetId) ?: return sourceDueDate
            val targetBudget = db.budgetDao().getBudgetById(targetBudgetId) ?: return sourceDueDate

            val cal = Calendar.getInstance()
            cal.timeInMillis = sourceDueDate

            when (recurrenceType) {
                RecurrenceType.MONTHLY_SAME_DAY -> {
                    val monthDiff = (targetBudget.year - sourceBudget.year) * 12 +
                                   (targetBudget.month - sourceBudget.month)
                    cal.add(Calendar.MONTH, monthDiff)
                }
                RecurrenceType.EVERY_X_DAYS -> {
                    val monthDiff = (targetBudget.year - sourceBudget.year) * 12 +
                                   (targetBudget.month - sourceBudget.month)
                    val daysToAdd = (recurrenceInterval ?: 30) * monthDiff
                    cal.add(Calendar.DAY_OF_MONTH, daysToAdd)
                }
                RecurrenceType.NONE -> {
                    // No shift needed
                }
            }
            return cal.timeInMillis
        }

        // 10. Insert new expenses
        for (key in keysToInsert) {
            val sourceExpense = sourceMap[key]!!
            val targetCategoryId = categoryMap[sourceExpense.categoryId] ?: continue
            val newDueDate = shiftDueDate(sourceExpense.dueDate, fromBudgetId, toBudgetId, sourceExpense.recurrenceType, sourceExpense.recurrenceInterval)
            val newExpense = Expense(
                categoryId = targetCategoryId,
                description = sourceExpense.description,
                amount = sourceExpense.amount,
                dueDate = newDueDate,
                recurrenceType = sourceExpense.recurrenceType,
                recurrenceInterval = sourceExpense.recurrenceInterval,
                sourceExpenseId = sourceExpense.id,
                createdAt = System.currentTimeMillis(),
                isActive = sourceExpense.isActive
            )
            db.expenseDao().insert(newExpense)
            val day = getDayOfMonth(newDueDate)
            ensureChecklistItem(toBudgetId, day)
            Timber.d("Inserted recurring expense: ${sourceExpense.description} to budget $toBudgetId")
        }

        // 11. Update existing expenses
        for (key in keysToUpdate) {
            val sourceExpense = sourceMap[key]!!
            val targetExpense = targetMap[key]!!

            // Skip if user has overridden this expense in the target month
            if (targetExpense.isOverridden) {
                Timber.d("Skipping update for overridden expense: ${targetExpense.description}")
                continue
            }

            val newDueDate = shiftDueDate(sourceExpense.dueDate, fromBudgetId, toBudgetId, sourceExpense.recurrenceType, sourceExpense.recurrenceInterval)
            if (targetExpense.amount != sourceExpense.amount ||
                targetExpense.description != sourceExpense.description ||
                targetExpense.dueDate != newDueDate ||
                targetExpense.recurrenceType != sourceExpense.recurrenceType ||
                targetExpense.recurrenceInterval != sourceExpense.recurrenceInterval) {
                val updatedExpense = targetExpense.copy(
                    amount = sourceExpense.amount,
                    description = sourceExpense.description,
                    dueDate = newDueDate,
                    recurrenceType = sourceExpense.recurrenceType,
                    recurrenceInterval = sourceExpense.recurrenceInterval,
                    updatedAt = System.currentTimeMillis()
                )
                db.expenseDao().update(updatedExpense)
                val day = getDayOfMonth(newDueDate)
                ensureChecklistItem(toBudgetId, day)
                Timber.d("Updated recurring expense: ${sourceExpense.description} in budget $toBudgetId")
            }
        }

        // 12. Delete expenses that no longer exist in source
        for (key in keysToDelete) {
            val targetExpense = targetMap[key]!!
            db.expenseDao().delete(targetExpense)
            val day = getDayOfMonth(targetExpense.dueDate)
            val remaining = db.expenseDao().getExpensesForBudget(toBudgetId).first()
                .any { getDayOfMonth(it.dueDate) == day && it.id != targetExpense.id }
            if (!remaining) {
                db.dailyChecklistDao().getChecklistItem(toBudgetId, day)?.let {
                    db.dailyChecklistDao().delete(it)
                }
            }
            Timber.d("Deleted recurring expense: ${targetExpense.description} from budget $toBudgetId")
        }
    }

    suspend fun getOrCreateBudgetChain(targetMonth: Int, targetYear: Int): Pair<String, Boolean> {
        return budgetChainMutex.withLock {
            Timber.d("getOrCreateBudgetChain: target $targetMonth/$targetYear")
            var created = false  // <-- NEW: track whether we create the target budget

            // 1. Check if the target budget already exists
            val current = getBudget(targetMonth, targetYear).first()
            if (current != null) {
                Timber.d("Target budget already exists: id=${current.id}")
                ensureMonthSettings(current.id)
                return@withLock Pair(current.id, false)  // <-- CHANGED: return false because it existed
            }

            // 2. Find the previous budget
            val previous = db.budgetDao().findPreviousBudget(targetMonth, targetYear)
            if (previous == null) {
                // No previous budget → create the target directly
                Timber.d("No previous budget found, creating target directly")
                val newBudget = Budget(month = targetMonth, year = targetYear)
                insertBudget(newBudget)
                ensureMonthSettings(newBudget.id)
                return@withLock Pair(newBudget.id, true)  // <-- CHANGED: return true because we created it
            }

            Timber.d("Previous budget found: ${previous.year}-${previous.month} id=${previous.id}")

            // 3. Build the chain from previous month up to target
            var fromBudgetId = previous.id
            val cal = Calendar.getInstance().apply {
                set(previous.year, previous.month - 1, 1)
                add(Calendar.MONTH, 1) // start from the month after previous
            }

            while (cal.get(Calendar.YEAR) < targetYear ||
                (cal.get(Calendar.YEAR) == targetYear && cal.get(Calendar.MONTH) + 1 <= targetMonth)) {

                val month = cal.get(Calendar.MONTH) + 1
                val year = cal.get(Calendar.YEAR)

                Timber.d("Creating budget for $year-$month")
                val newBudget = Budget(month = month, year = year)
                insertBudget(newBudget)

                // <-- NEW: check if this newly created budget is the one we were asked for
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
            return@withLock Pair(fromBudgetId, created)  // <-- CHANGED: return the flag
        }
    }

    suspend fun updateAllIncomesCurrency(newCurrency: String) {
        db.incomeDao().updateAllCurrency(newCurrency)
    }
}