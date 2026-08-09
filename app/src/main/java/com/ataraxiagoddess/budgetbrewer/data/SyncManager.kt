/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.data

import android.content.Context
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber

@Serializable
private data class IdResponse(val id: String)

/**
 * Manages synchronization between local Room database and Supabase.
 * Uses UUID primary keys for all entities (stored as String in local, as UUID in Supabase).
 */
class SyncManager(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val supabase = SupabaseClient.client

    private suspend fun queueOperation(operation: String, table: String, recordId: String, userId: String) {
        val pending = PendingSync(
            operation = operation,
            table = table,
            recordId = recordId,
            userId = userId
        )
        db.pendingSyncDao().insert(pending)
    }

    // ========== PUBLIC API ==========

    /**
     * Upload all local data to Supabase for the given user.
     * Should be called after successful sign‑in.
     */
    suspend fun uploadAllData(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                uploadAllBudgets(userId)
                uploadAllIncomes(userId)
                uploadAllCategories(userId)
                uploadAllExpenses(userId)
                uploadAllAllocations(userId)
                uploadAllDailyChecklist(userId)
                uploadAllSpendingEntries(userId)
                uploadAllMonthSettings(userId)
                uploadAllDailyIncomeAssignments(userId)
                uploadAllSavingsBuckets(userId)
                uploadAllSavingsTransactions(userId)
                Timber.d("All data uploaded for user $userId")
            } catch (e: Exception) {
                Timber.e(e, "Upload all data failed")
                // Optionally queue for retry
            }
        }
    }

    suspend fun userHasData(userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = supabase.postgrest["budgets"].select(Columns.raw("id")) {
                    filter { eq("user_id", userId) }
                    limit(1)
                }.decodeList<IdResponse>()
                val hasData = result.isNotEmpty()
                Timber.d("userHasData for $userId: $hasData")
                hasData
            } catch (e: Exception) {
                Timber.e(e, "userHasData failed for $userId")
                false
            }
        }
    }

    /**
     * Upload a single budget.
     */
    suspend fun uploadBudget(budget: Budget, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val payload = BudgetPayload(
                    id = budget.id,
                    month = budget.month,
                    year = budget.year,
                    created_at = budget.createdAt,
                    updated_at = budget.updatedAt,
                    user_id = userId
                )
                supabase.postgrest["budgets"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadBudget failed, queueing")
                queueOperation("INSERT", "budgets", budget.id, userId)
            }
        }
    }

    /**
     * Upload a single income.
     */
    suspend fun uploadIncome(income: Income, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val payload = IncomePayload(
                    id = income.id,
                    budget_id = income.budgetId,
                    source_name = income.sourceName,
                    currency = income.currency,
                    amount = income.amount,
                    frequency = income.frequency.name,
                    is_tips = income.isTips,
                    week_number = income.weekNumber,
                    tips_order = income.tipsOrder,
                    created_at = income.createdAt,
                    updated_at = income.updatedAt,
                    user_id = userId
                )
                supabase.postgrest["incomes"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadIncome failed, queueing")
                queueOperation("INSERT", "incomes", income.id, userId)
            }
        }
    }

    /**
     * Upload a single expense category.
     */
    suspend fun uploadCategory(category: ExpenseCategory, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val payload = ExpenseCategoryPayload(
                    id = category.id,
                    budget_id = category.budgetId,
                    name = category.name,
                    color = category.color,
                    display_order = category.displayOrder,
                    created_at = category.createdAt,
                    updated_at = category.updatedAt,
                    user_id = userId
                )
                supabase.postgrest["expense_categories"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadCategory failed, queueing")
                queueOperation("INSERT", "expense_categories", category.id, userId)
            }
        }
    }

    /**
     * Upload a single expense.
     */
    suspend fun uploadExpense(expense: Expense, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val category = db.expenseCategoryDao().getCategoryById(expense.categoryId)
                if (category != null) {
                    val existing = supabase.postgrest["expense_categories"].select(Columns.raw("id")) {
                        filter { eq("id", category.id) }
                    }.decodeList<IdResponse>().firstOrNull()
                    if (existing == null) {
                        uploadCategory(category, userId)
                    }
                }
                val payload = ExpensePayload(
                    id = expense.id,
                    category_id = expense.categoryId,
                    description = expense.description,
                    amount = expense.amount,
                    due_date = expense.dueDate,
                    recurrence_type = expense.recurrenceType.name,
                    recurrence_interval = expense.recurrenceInterval,
                    source_expense_id = expense.sourceExpenseId,
                    is_overridden = expense.isOverridden,
                    created_at = expense.createdAt,
                    is_active = expense.isActive,
                    updated_at = expense.updatedAt,
                    user_id = userId
                )
                supabase.postgrest["expenses"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadExpense failed, queueing")
                queueOperation("INSERT", "expenses", expense.id, userId)
            }
        }
    }

    /**
     * Upload a single allocation.
     */
    suspend fun uploadAllocation(allocation: Allocation, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val payload = AllocationPayload(
                    id = allocation.id,
                    budget_id = allocation.budgetId,
                    savings_amount = allocation.savingsAmount,
                    savings_is_percentage = allocation.savingsIsPercentage,
                    spending_amount = allocation.spendingAmount,
                    spending_is_percentage = allocation.spendingIsPercentage,
                    created_at = allocation.createdAt,
                    updated_at = allocation.updatedAt,
                    user_id = userId
                )
                supabase.postgrest["allocations"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadAllocation failed, queueing")
                queueOperation("INSERT", "allocations", allocation.id, userId)
            }
        }
    }

    /**
     * Upload a single daily checklist item.
     */
    suspend fun uploadDailyChecklistItem(item: DailyChecklist, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val payload = DailyChecklistPayload(
                    id = item.id,
                    budget_id = item.budgetId,
                    day_of_month = item.dayOfMonth,
                    is_checked = item.isChecked,
                    updated_at = item.updatedAt,
                    user_id = userId
                )
                supabase.postgrest["daily_checklist"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadDailyChecklist failed, queueing")
                queueOperation("INSERT", "daily_checklist", item.id, userId)
            }
        }
    }

    /**
     * Upload a single spending entry.
     */
    suspend fun uploadSpendingEntry(entry: SpendingEntry, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val payload = SpendingEntryPayload(
                    id = entry.id,
                    budget_id = entry.budgetId,
                    date = entry.date,
                    source = entry.source,
                    amount = entry.amount,
                    tag = entry.tag,
                    note = entry.note,
                    created_at = entry.createdAt,
                    updated_at = entry.updatedAt,
                    user_id = userId
                )
                supabase.postgrest["spending_entries"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadSpendingEntry failed, queueing")
                queueOperation("INSERT", "spending_entries", entry.id, userId)
            }
        }
    }

    // --- Savings Buckets Sync ---
    suspend fun uploadSavingsBucket(bucket: SavingsBucket, userId: String) {
        withContext(Dispatchers.IO) {
            Timber.d("uploadSavingsBucket – userId = $userId")
            Timber.d("uploadSavingsBucket – upsert completed")
            try {
                val payload = SavingsBucketPayload(
                    id = bucket.id,
                    user_id = userId,
                    name = bucket.name,
                    type = bucket.type.name,
                    current_amount = bucket.current_amount,
                    target_amount = bucket.target_amount,
                    color_hex = bucket.color_hex,
                    is_archived = bucket.is_archived,
                    created_at = bucket.created_at,
                    updated_at = bucket.updated_at
                )
                supabase.postgrest["savings_buckets"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadSavingsBucket failed, queueing")
                queueOperation("INSERT", "savings_buckets", bucket.id, userId)
            }
        }
    }

    // --- Savings Transactions Sync ---
    suspend fun uploadSavingsTransaction(transaction: SavingsTransaction, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val payload = SavingsTransactionPayload(
                    id = transaction.id,
                    bucket_id = transaction.bucket_id,
                    amount = transaction.amount,
                    date = transaction.date,
                    type = transaction.type.name,
                    created_at = transaction.created_at,
                    updated_at = transaction.updated_at
                )
                supabase.postgrest["savings_transactions"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadSavingsTransaction failed, queueing")
                queueOperation("INSERT", "savings_transactions", transaction.id, userId)
            }
        }
    }

    /**
     * Upload a single month setting.
     */
    suspend fun uploadMonthSetting(setting: MonthSettings, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val payload = MonthSettingsPayload(
                    id = setting.id,
                    budget_id = setting.budgetId,
                    month_start_amount = setting.monthStartAmount,
                    month_start_overridden = setting.monthStartOverridden,
                    tips_enabled = setting.tipsEnabled,
                    pay_frequency = setting.payFrequency,
                    created_at = setting.createdAt,
                    updated_at = setting.updatedAt,
                    user_id = userId
                )
                supabase.postgrest["month_settings"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadMonthSettings failed, queueing")
                queueOperation("INSERT", "month_settings", setting.id, userId)
            }
        }
    }

    /**
     * Upload a single daily income assignment.
     */
    suspend fun uploadDailyIncomeAssignment(assignment: DailyIncomeAssignment, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val payload = DailyIncomeAssignmentPayload(
                    id = assignment.id,
                    budget_id = assignment.budgetId,
                    income_id = assignment.incomeId,
                    day_of_month = assignment.dayOfMonth,
                    created_at = assignment.createdAt,
                    updated_at = assignment.updatedAt,
                    user_id = userId
                )
                supabase.postgrest["daily_income_assignments"].upsert(payload)
            } catch (e: Exception) {
                Timber.e(e, "uploadDailyIncomeAssignment failed, queueing")
                queueOperation("INSERT", "daily_income_assignments", assignment.id, userId)
            }
        }
    }

    // ========== DELETE METHODS ==========

    /**
     * Delete an income from Supabase.
     */
    suspend fun deleteIncome(incomeId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["incomes"].delete {
                    filter {
                        eq("id", incomeId)
                        eq("user_id", userId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "deleteIncome failed, queueing")
                queueOperation("DELETE", "incomes", incomeId, userId)
            }
        }
    }

    /**
     * Delete a category from Supabase.
     */
    suspend fun deleteCategory(categoryId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["expense_categories"].delete {
                    filter {
                        eq("id", categoryId)
                        eq("user_id", userId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "deleteCategory failed, queueing")
                queueOperation("DELETE", "expense_categories", categoryId, userId)
            }
        }
    }

    /**
     * Delete an expense from Supabase.
     */
    suspend fun deleteExpense(expenseId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["expenses"].delete {
                    filter {
                        eq("id", expenseId)
                        eq("user_id", userId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "deleteExpense failed, queueing")
                queueOperation("DELETE", "expenses", expenseId, userId)
            }
        }
    }

    /**
     * Delete an allocation from Supabase.
     */
    suspend fun deleteAllocation(allocationId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["allocations"].delete {
                    filter {
                        eq("id", allocationId)
                        eq("user_id", userId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "deleteAllocation failed, queueing")
                queueOperation("DELETE", "allocations", allocationId, userId)
            }
        }
    }

    /**
     * Delete a spending entry from Supabase.
     */
    suspend fun deleteSpendingEntry(entryId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["spending_entries"].delete {
                    filter {
                        eq("id", entryId)
                        eq("user_id", userId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "deleteSpendingEntry failed, queueing")
                queueOperation("DELETE", "spending_entries", entryId, userId)
            }
        }
    }

    /**
     * Delete a savings bucket from Supabase.
     */
    suspend fun deleteSavingsBucket(bucketId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["savings_buckets"].delete {
                    filter {
                        eq("id", bucketId)
                        eq("user_id", userId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "deleteSavingsBucket failed, queueing")
            }
        }
    }

    /**
     * Delete a savings bucket transaction from Supabase.
     */
    suspend fun deleteSavingsTransaction(transactionId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["savings_transactions"].delete {
                    filter {
                        eq("id", transactionId)
                        // No user_id filter – RLS will check the bucket's user_id via the bucket relation.
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "deleteSavingsTransaction failed, queueing")
                queueOperation("DELETE", "savings_transactions", transactionId, userId)
            }
        }
    }

    suspend fun deleteDailyChecklistItem(itemId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["daily_checklist"].delete {
                    filter {
                        eq("id", itemId)
                        eq("user_id", userId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "deleteDailyChecklistItem failed, queueing")
                queueOperation("DELETE", "daily_checklist", itemId, userId)
            }
        }
    }

    /**
     * Delete a daily income assignment from Supabase.
     */
    suspend fun deleteDailyIncomeAssignment(assignmentId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["daily_income_assignments"].delete {
                    filter {
                        eq("id", assignmentId)
                        eq("user_id", userId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "deleteDailyIncomeAssignment failed, queueing")
                queueOperation("DELETE", "daily_income_assignments", assignmentId, userId)
            }
        }
    }

    suspend fun deleteMonthSetting(settingId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["month_settings"].delete {
                    filter {
                        eq("id", settingId)
                        eq("user_id", userId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "deleteMonthSetting failed, queueing")
                queueOperation("DELETE", "month_settings", settingId, userId)
            }
        }
    }

    // ========== DOWNLOAD METHODS ==========

    /**
     * Download all data for a user and replace local database.
     */
    suspend fun downloadAllData(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                clearLocalData()
                downloadBudgets(userId)
                downloadIncomes(userId)
                downloadCategories(userId)
                downloadExpenses(userId)
                downloadAllocations(userId)
                downloadDailyChecklist(userId)
                downloadSpendingEntries(userId)
                downloadMonthSettings(userId)
                downloadDailyIncomeAssignments(userId)
                downloadSavingsBuckets(userId)
                downloadSavingsTransactions()
                Timber.d("All data downloaded for user $userId")
            } catch (e: Exception) {
                Timber.e(e, "Download all data failed")
            }
        }
    }

    private suspend fun downloadBudgets(userId: String) {
        val response = supabase.postgrest["budgets"].select(Columns.raw("*")) {
            filter { eq("user_id", userId) }
        }.decodeList<Budget>()
        response.forEach { db.budgetDao().insert(it) }
    }

    private suspend fun downloadIncomes(userId: String) {
        val response = supabase.postgrest["incomes"].select(Columns.raw("*")) {
            filter { eq("user_id", userId) }
        }.decodeList<Income>()
        response.forEach { db.incomeDao().insert(it) }
    }

    private suspend fun downloadCategories(userId: String) {
        val response = supabase.postgrest["expense_categories"].select(Columns.raw("*")) {
            filter { eq("user_id", userId) }
        }.decodeList<ExpenseCategory>()
        response.forEach { db.expenseCategoryDao().insert(it) }
    }

    private suspend fun downloadExpenses(userId: String) {
        val response = supabase.postgrest["expenses"].select(Columns.raw("*")) {
            filter { eq("user_id", userId) }
        }.decodeList<Expense>()
        response.forEach { db.expenseDao().insert(it) }
    }

    private suspend fun downloadAllocations(userId: String) {
        val response = supabase.postgrest["allocations"].select(Columns.raw("*")) {
            filter { eq("user_id", userId) }
        }.decodeList<Allocation>()
        response.forEach { db.allocationDao().insert(it) }
    }

    private suspend fun downloadDailyChecklist(userId: String) {
        val response = supabase.postgrest["daily_checklist"].select(Columns.raw("*")) {
            filter { eq("user_id", userId) }
        }.decodeList<DailyChecklist>()
        response.forEach { db.dailyChecklistDao().insert(it) }
    }

    private suspend fun downloadSpendingEntries(userId: String) {
        val response = supabase.postgrest["spending_entries"].select(Columns.raw("*")) {
            filter { eq("user_id", userId) }
        }.decodeList<SpendingEntry>()
        response.forEach { db.spendingEntryDao().insert(it) }
    }

    private suspend fun downloadSavingsBuckets(userId: String) {
        val response = supabase.postgrest["savings_buckets"].select(Columns.raw("*")) {
            filter { eq("user_id", userId) }
        }.decodeList<SavingsBucket>()
        response.forEach { db.savingsBucketDao().insert(it) }
    }

    private suspend fun downloadSavingsTransactions() {
        val bucketIds = db.savingsBucketDao().getAllBuckets().first().map { it.id }
        if (bucketIds.isEmpty()) return

        val allTransactions = mutableListOf<SavingsTransaction>()
        bucketIds.forEach { bucketId ->
            val response = supabase.postgrest["savings_transactions"].select(Columns.raw("*")) {
                filter { eq("bucket_id", bucketId) }
            }.decodeList<SavingsTransaction>()
            allTransactions.addAll(response)
        }
        allTransactions.forEach { db.savingsTransactionDao().insert(it) }
    }

    private suspend fun downloadMonthSettings(userId: String) {
        val response = supabase.postgrest["month_settings"].select(Columns.raw("*")) {
            filter { eq("user_id", userId) }
        }.decodeList<MonthSettings>()
        response.forEach { db.monthSettingsDao().insert(it) }
    }

    private suspend fun downloadDailyIncomeAssignments(userId: String) {
        val response = supabase.postgrest["daily_income_assignments"].select(Columns.raw("*")) {
            filter { eq("user_id", userId) }
        }.decodeList<DailyIncomeAssignment>()
        response.forEach { db.dailyIncomeAssignmentDao().insert(it) }
    }

    // ========== BATCH UPLOAD HELPERS (used by uploadAllData) ==========

    private suspend fun uploadAllBudgets(userId: String) {
        db.budgetDao().getAllBudgetsSync().forEach { uploadBudget(it, userId) }
    }

    private suspend fun uploadAllIncomes(userId: String) {
        db.incomeDao().getAllIncomesSync().forEach { uploadIncome(it, userId) }
    }

    private suspend fun uploadAllCategories(userId: String) {
        db.expenseCategoryDao().getAllCategoriesSync().forEach { uploadCategory(it, userId) }
    }

    private suspend fun uploadAllExpenses(userId: String) {
        db.expenseDao().getAllExpensesSync().forEach { uploadExpense(it, userId) }
    }

    private suspend fun uploadAllAllocations(userId: String) {
        db.allocationDao().getAllAllocationsSync().forEach { uploadAllocation(it, userId) }
    }

    private suspend fun uploadAllDailyChecklist(userId: String) {
        db.dailyChecklistDao().getAllChecklistSync().forEach { uploadDailyChecklistItem(it, userId) }
    }

    private suspend fun uploadAllSpendingEntries(userId: String) {
        db.spendingEntryDao().getAllSpendingEntriesSync().forEach { uploadSpendingEntry(it, userId) }
    }

    private suspend fun uploadAllSavingsBuckets(userId: String) {
        db.savingsBucketDao().getAllBucketsSync().forEach { uploadSavingsBucket(it, userId) }
    }

    private suspend fun uploadAllSavingsTransactions(userId: String) {
        db.savingsTransactionDao().getAllTransactionsSync().forEach { uploadSavingsTransaction(it, userId) }
    }

    private suspend fun uploadAllMonthSettings(userId: String) {
        db.monthSettingsDao().getAllMonthSettingsSync().forEach { uploadMonthSetting(it, userId) }
    }

    private suspend fun uploadAllDailyIncomeAssignments(userId: String) {
        db.dailyIncomeAssignmentDao().getAllAssignmentsSync().forEach { uploadDailyIncomeAssignment(it, userId) }
    }

    // ========== LOCAL CLEAR ==========

    suspend fun clearLocalData() {
        // Order matters due to foreign key constraints
        db.dailyIncomeAssignmentDao().deleteAll()
        db.spendingEntryDao().deleteAll()
        db.dailyChecklistDao().deleteAll()
        db.expenseDao().deleteAll()
        db.expenseCategoryDao().deleteAll()
        db.incomeDao().deleteAll()
        db.allocationDao().deleteAll()
        db.monthSettingsDao().deleteAll()
        db.savingsTransactionDao().deleteAll()
        db.savingsBucketDao().deleteAll()
        db.budgetDao().deleteAll()
    }
}
