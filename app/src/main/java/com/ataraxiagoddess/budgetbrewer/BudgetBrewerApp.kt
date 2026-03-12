package com.ataraxiagoddess.budgetbrewer

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.SupabaseClient
import com.ataraxiagoddess.budgetbrewer.data.SyncWorker
import com.ataraxiagoddess.budgetbrewer.ui.month.MonthRolloverWorker
import com.ataraxiagoddess.budgetbrewer.util.AppLockManager
import com.ataraxiagoddess.budgetbrewer.util.CurrencyPrefs
import com.ataraxiagoddess.budgetbrewer.util.SyncHelper
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

class BudgetBrewerApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Supabase with session persistence
        SupabaseClient.initialize(this)

        // Restore user ID if session exists
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
        if (userId != null) {
            AuthManager.saveUserId(this, userId)
        }

        CurrencyPrefs.init(this)

        // Initialize AppLockManager (secure storage)
        AppLockManager.init(this)

        val prefs = getSharedPreferences("budget_prefs", MODE_PRIVATE)
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)
        prefs.edit {
            putInt("selected_month", currentMonth)
            putInt("selected_year", currentYear)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        scheduleMonthRollover()
        scheduleSyncWorker()

        val settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeMode = settingsPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)

        // --- Register network callback for instant sync ---
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // Network is now available – trigger sync
                CoroutineScope(Dispatchers.IO).launch {
                    SyncHelper.triggerSyncIfNeeded(this@BudgetBrewerApp)
                }
            }
        })
    }

    private fun scheduleMonthRollover() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val rolloverRequest = PeriodicWorkRequestBuilder<MonthRolloverWorker>(
            1, TimeUnit.DAYS
        ).setConstraints(constraints)
            .setInitialDelay(calculateTimeToMidnight(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "month_rollover",
            ExistingPeriodicWorkPolicy.KEEP,
            rolloverRequest
        )
    }

    // In BudgetBrewerApp.kt, inside onCreate, after scheduleMonthRollover()

    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            8, TimeUnit.HOURS,   // repeat interval
            2, TimeUnit.HOURS    // flex interval – allows system to run within a window
        ).setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "offline_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun calculateTimeToMidnight(): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 24)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return calendar.timeInMillis - now
    }
}