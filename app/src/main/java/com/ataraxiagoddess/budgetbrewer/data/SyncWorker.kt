/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import timber.log.Timber

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_DOWNLOAD_ALL = "download_all"
    }

    override suspend fun doWork(): Result {
        // If this worker was scheduled for an initial download, do that and return
        if (inputData.getBoolean(KEY_DOWNLOAD_ALL, false)) {
            Timber.d("SyncWorker performing initial download")
            val authManager = AuthManager
            val userId = authManager.getUserId(applicationContext) ?: return Result.failure()
            val syncManager = SyncManager(applicationContext)
            try {
                syncManager.downloadAllData(userId)
                return Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Initial download failed")
                return Result.retry()
            }
        }
        Timber.d("SyncWorker started")
        val db = AppDatabase.getDatabase(applicationContext)
        val pendingList = db.pendingSyncDao().getAllPendingSync()
        if (pendingList.isEmpty()) {
            Timber.d("No pending sync items")
            return Result.success()
        }

        val syncManager = SyncManager(applicationContext)

        var anyFailed = false

        suspend fun processIncome(pending: PendingSync) {
            when (pending.operation) {
                "INSERT", "UPDATE" -> {
                    val income = db.incomeDao().getIncomeById(pending.recordId) ?: return
                    syncManager.uploadIncome(income, pending.userId)
                }
                "DELETE" -> {
                    syncManager.deleteIncome(pending.recordId, pending.userId)
                }
            }
        }

        suspend fun processExpense(pending: PendingSync) {
            when (pending.operation) {
                "INSERT", "UPDATE" -> {
                    val expense = db.expenseDao().getExpenseById(pending.recordId) ?: return
                    syncManager.uploadExpense(expense, pending.userId)
                }
                "DELETE" -> {
                    syncManager.deleteExpense(pending.recordId, pending.userId)
                }
            }
        }

        suspend fun processCategory(pending: PendingSync) {
            when (pending.operation) {
                "INSERT", "UPDATE" -> {
                    val category = db.expenseCategoryDao().getCategoryById(pending.recordId) ?: return
                    syncManager.uploadCategory(category, pending.userId)
                }
                "DELETE" -> {
                    syncManager.deleteCategory(pending.recordId, pending.userId)
                }
            }
        }

        suspend fun processAllocation(pending: PendingSync) {
            when (pending.operation) {
                "INSERT", "UPDATE" -> {
                    val allocation = db.allocationDao().getAllocationById(pending.recordId) ?: return
                    syncManager.uploadAllocation(allocation, pending.userId)
                }
                "DELETE" -> {
                    syncManager.deleteAllocation(pending.recordId, pending.userId)
                }
            }
        }

        suspend fun processSpending(pending: PendingSync) {
            when (pending.operation) {
                "INSERT", "UPDATE" -> {
                    val spending = db.spendingEntryDao().getSpendingEntryById(pending.recordId) ?: return
                    syncManager.uploadSpendingEntry(spending, pending.userId)
                }
                "DELETE" -> {
                    syncManager.deleteSpendingEntry(pending.recordId, pending.userId)
                }
            }
        }

        suspend fun processSavingsBucket(pending: PendingSync) {
            when (pending.operation) {
                "INSERT", "UPDATE" -> {
                    val bucket = db.savingsBucketDao().getBucketById(pending.recordId) ?: return
                    syncManager.uploadSavingsBucket(bucket, pending.userId)
                }
                "DELETE" -> {
                    syncManager.deleteSavingsBucket(pending.recordId, pending.userId)
                }
            }
        }

        suspend fun processSavingsTransaction(pending: PendingSync) {
            when (pending.operation) {
                "INSERT", "UPDATE" -> {
                    val transaction = db.savingsTransactionDao().getTransactionById(pending.recordId) ?: return
                    syncManager.uploadSavingsTransaction(transaction, pending.userId)
                }
                "DELETE" -> {
                    // Transactions are rarely deleted directly; handle if needed
                }
            }
        }

        suspend fun processDailyChecklist(pending: PendingSync) {
            when (pending.operation) {
                "INSERT", "UPDATE" -> {
                    val item = db.dailyChecklistDao().getChecklistItemById(pending.recordId) ?: return
                    syncManager.uploadDailyChecklistItem(item, pending.userId)
                }
                "DELETE" -> {
                    syncManager.deleteDailyChecklistItem(pending.recordId, pending.userId)
                }
            }
        }

        suspend fun processMonthSettings(pending: PendingSync) {
            when (pending.operation) {
                "INSERT", "UPDATE" -> {
                    val settings = db.monthSettingsDao().getMonthSettingsById(pending.recordId) ?: return
                    syncManager.uploadMonthSetting(settings, pending.userId)
                }
                "DELETE" -> {
                    // Month settings are rarely deleted, but if they are, handle it
                    syncManager.deleteMonthSetting(pending.recordId, pending.userId)
                }
            }
        }

        suspend fun processDailyIncomeAssignment(pending: PendingSync) {
            when (pending.operation) {
                "INSERT", "UPDATE" -> {
                    val assignment = db.dailyIncomeAssignmentDao().getAssignmentById(pending.recordId) ?: return
                    syncManager.uploadDailyIncomeAssignment(assignment, pending.userId)
                }
                "DELETE" -> {
                    syncManager.deleteDailyIncomeAssignment(pending.recordId, pending.userId)
                }
            }
        }

        for (pending in pendingList) {
            try {
                when (pending.table) {
                    "incomes" -> processIncome(pending)
                    "expenses" -> processExpense(pending)
                    "expense_categories" -> processCategory(pending)
                    "allocations" -> processAllocation(pending)
                    "spending_entries" -> processSpending(pending)
                    "daily_checklist" -> processDailyChecklist(pending)
                    "month_settings" -> processMonthSettings(pending)
                    "daily_income_assignments" -> processDailyIncomeAssignment(pending)
                    "savings_buckets" -> processSavingsBucket(pending)
                    "savings_transactions" -> processSavingsTransaction(pending)
                }
                // On success, remove from queue
                db.pendingSyncDao().delete(pending)
                Timber.d("Processed ${pending.table} ${pending.recordId}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to process pending item: $pending")
                anyFailed = true
                // Leave it in queue for next retry
            }
        }
        val remaining = db.pendingSyncDao().getAllPendingSync().size
        Timber.d("SyncWorker finished, remaining pending: $remaining")
        return if (anyFailed) Result.retry() else Result.success()
    }
}
