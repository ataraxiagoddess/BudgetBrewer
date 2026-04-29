package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ataraxiagoddess.budgetbrewer.MainActivity
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.databinding.ActivitySavingsBinding
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.calendar.MonthlyCalendarActivity
import com.ataraxiagoddess.budgetbrewer.ui.expenses.MonthlyExpenseListActivity
import com.ataraxiagoddess.budgetbrewer.ui.finances.IncomeExpensesActivity
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity
import com.ataraxiagoddess.budgetbrewer.ui.spending.SpendingActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SavingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySavingsBinding
    private lateinit var viewModel: SavingsViewModel
    private lateinit var adapter: SavingsBucketAdapter

    override val currentNavDestination: NavDestination = NavDestination.SAVINGS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySavingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Create ViewModel with SavedStateHandle support
        val db = AppDatabase.getDatabase(this)
        val repository = BudgetRepository(db)
        val factory = SavingsViewModelFactory(repository, this)
        viewModel = ViewModelProvider(this, factory)[SavingsViewModel::class.java]

        // Set up RecyclerView
        adapter = SavingsBucketAdapter()
        binding.recyclerViewBuckets.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewBuckets.adapter = adapter

        binding.fabAddBucket.setOnClickListener {
            showCreateBucketDialog()
        }

        // Click listener for Add Bucket button (in empty state)
        binding.emptyStateContainer.buttonAddBucket.setOnClickListener {
            showCreateBucketDialog()
        }

        // Observe UI state
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is SavingsUiState.Loading -> {
                        showLoading()
                    }
                    is SavingsUiState.Success -> {
                        hideLoading()
                        if (state.isEmpty) {
                            showEmptyState()
                        } else {
                            showBucketList(state.buckets)
                        }
                        binding.fabAddBucket.isEnabled = !state.maxBucketsReached
                        binding.fabAddBucket.backgroundTintList = if (state.maxBucketsReached) {
                            ColorStateList.valueOf(ContextCompat.getColor(this@SavingsActivity, R.color.text_disabled))
                        } else {
                            ColorStateList.valueOf(ContextCompat.getColor(this@SavingsActivity, R.color.focus_highlight))
                        }
                    }
                    is SavingsUiState.Error -> {
                        hideLoading()
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Observe one-shot events
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is SavingsUiEvent.BucketAdded -> {
                        Snackbar.make(binding.root, getString(R.string.bucket_added), Snackbar.LENGTH_SHORT).show()
                    }
                    is SavingsUiEvent.ShowError -> {
                        Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }

        // Listen for month changes from BaseActivity
        addMonthChangeListener { month -> viewModel.updateMonth(month) }
    }

    private fun showCreateBucketDialog() {
        val dialog = CreateBucketDialogFragment(
            onBucketCreated = { bucket -> viewModel.createBucket(bucket) },
            onShowSnackbar = { message -> this.showSnackbar(message) }
        )
        dialog.show(supportFragmentManager, "CreateBucketDialog")
    }

    private fun showEmptyState() {
        binding.emptyStateContainer.root.visibility = View.VISIBLE
        binding.recyclerViewBuckets.visibility = View.GONE
    }

    private fun showBucketList(buckets: List<SavingsBucket>) {
        binding.emptyStateContainer.root.visibility = View.GONE
        binding.recyclerViewBuckets.visibility = View.VISIBLE
        adapter.submitList(buckets)
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    // ==================== NAVIGATION OVERRIDES ====================
    override fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
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
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }
}