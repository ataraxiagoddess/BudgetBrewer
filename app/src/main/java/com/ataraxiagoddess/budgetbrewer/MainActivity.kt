package com.ataraxiagoddess.budgetbrewer

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SyncManager
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.calendar.MonthlyCalendarActivity
import com.ataraxiagoddess.budgetbrewer.ui.expenses.MonthlyExpenseListActivity
import com.ataraxiagoddess.budgetbrewer.ui.finances.IncomeExpensesActivity
import com.ataraxiagoddess.budgetbrewer.ui.home.HomeFragment
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity
import com.ataraxiagoddess.budgetbrewer.ui.spending.SpendingActivity
import com.ataraxiagoddess.budgetbrewer.util.SyncHelper
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : BaseActivity() {

    override val currentNavDestination: NavDestination
        get() = NavDestination.HOME

    private lateinit var repository: BudgetRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("MainActivity onCreate - TEST LOG")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize repository
        val db = AppDatabase.getDatabase(this)
        repository = BudgetRepository(db)

        lifecycleScope.launch {
            val userId = AuthManager.getUserId(this@MainActivity)
            if (userId != null) {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val budgetCount = db.budgetDao().getAllBudgetsSync().size
                if (budgetCount == 0) {
                    // No local data, download from cloud
                    SyncManager(this@MainActivity).downloadAllData(userId)
                    // No need to refresh manually – Room flows will update the UI
                }
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            SyncHelper.triggerSyncIfNeeded(this@MainActivity)
        }
    }

    override fun navigateToFinances() {
        val intent = Intent(this, IncomeExpensesActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToExpenses() {
        val intent = Intent(this, MonthlyExpenseListActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToSpending() {
        val intent = Intent(this, SpendingActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToCalendar() {
        val intent = Intent(this, MonthlyCalendarActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }
}