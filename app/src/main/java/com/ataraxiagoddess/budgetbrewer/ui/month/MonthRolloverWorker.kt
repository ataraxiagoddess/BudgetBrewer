/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.month

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import timber.log.Timber
import java.util.Calendar

class MonthRolloverWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val repo = BudgetRepository(db)

            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            val currentYear = calendar.get(Calendar.YEAR)

            // Create/update budget chain for the new month
            val (_, _) = repo.getOrCreateBudgetChain(currentMonth, currentYear)

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Month rollover failed")
            Result.retry()
        }
}
