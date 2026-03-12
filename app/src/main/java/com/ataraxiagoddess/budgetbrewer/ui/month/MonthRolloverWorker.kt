package com.ataraxiagoddess.budgetbrewer.ui.month

import android.content.Context
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import timber.log.Timber
import java.util.Calendar

class MonthRolloverWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val repo = BudgetRepository(db)

            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            val currentYear = calendar.get(Calendar.YEAR)

            // Create/update budget chain for the new month
            repo.getOrCreateBudgetChain(currentMonth, currentYear)

            // Update shared preferences to point to new month
            val prefs = applicationContext.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putInt("selected_month", currentMonth)
                putInt("selected_year", currentYear)
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Month rollover failed")
            Result.retry()
        }
    }
}