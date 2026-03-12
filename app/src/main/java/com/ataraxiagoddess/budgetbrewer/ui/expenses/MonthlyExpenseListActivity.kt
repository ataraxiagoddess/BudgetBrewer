package com.ataraxiagoddess.budgetbrewer.ui.expenses

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.ataraxiagoddess.budgetbrewer.MainActivity
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.databinding.ActivityMonthlyExpenseBinding
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.base.MonthChangeListener
import com.ataraxiagoddess.budgetbrewer.ui.calendar.MonthlyCalendarActivity
import com.ataraxiagoddess.budgetbrewer.ui.finances.IncomeExpensesActivity
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity
import com.ataraxiagoddess.budgetbrewer.ui.spending.SpendingActivity
import com.ataraxiagoddess.budgetbrewer.util.GridSpacingItemDecoration
import kotlinx.coroutines.launch
import timber.log.Timber

class MonthlyExpenseListActivity : BaseActivity(), MonthChangeListener {

    override val currentNavDestination: NavDestination
        get() = NavDestination.EXPENSES

    private lateinit var binding: ActivityMonthlyExpenseBinding
    private lateinit var repository: BudgetRepository
    private lateinit var adapter: MonthlyExpenseListAdapter

    private val viewModel: MonthlyExpenseListViewModel by viewModels {
        MonthlyExpenseListViewModelFactory(repository, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonthlyExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getDatabase(this)
        repository = BudgetRepository(db)

        addMonthChangeListener(this)

        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = MonthlyExpenseListAdapter(
            onCheckboxChanged = { day, isChecked ->
                viewModel.toggleDayChecked(day, isChecked)
            }
        )

        val isTablet = resources.getBoolean(R.bool.is_tablet)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        when {
            isTablet -> {
                val gridLayoutManager = GridLayoutManager(this, 2)
                binding.recyclerView.layoutManager = gridLayoutManager

                val spacing = resources.getDimensionPixelSize(R.dimen.expenses_grid_spacing)
                while (binding.recyclerView.itemDecorationCount > 0) {
                    binding.recyclerView.removeItemDecorationAt(0)
                }
                binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(2, spacing, true))
            }
            !isTablet && isLandscape -> {
                val gridLayoutManager = GridLayoutManager(this, 2)
                binding.recyclerView.layoutManager = gridLayoutManager

                val spacing = resources.getDimensionPixelSize(R.dimen.expenses_grid_spacing)
                while (binding.recyclerView.itemDecorationCount > 0) {
                    binding.recyclerView.removeItemDecorationAt(0)
                }
                binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(2, spacing, true))
            }
            else -> {
                binding.recyclerView.layoutManager = LinearLayoutManager(this)
            }
        }

        binding.recyclerView.adapter = adapter
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is MonthlyExpenseListViewModel.MonthlyExpenseListUiState.Loading -> {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                    }
                    is MonthlyExpenseListViewModel.MonthlyExpenseListUiState.Success -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        adapter.submitList(state.days)
                        binding.tvTotalAmount.text = getString(R.string.total_expenses_format, state.totalAmount)
                        binding.tvRemainingAmount.text = getString(R.string.remaining_expenses_format, state.remainingAmount)
                    }
                    is MonthlyExpenseListViewModel.MonthlyExpenseListUiState.Error -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        android.widget.Toast.makeText(
                            this@MonthlyExpenseListActivity,
                            state.message,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onMonthChanged(month: Month) {
        Timber.d("MonthlyExpenseListActivity month changed: ${month.getDisplayName(this)}")
        viewModel.updateMonth(month)
    }

    override fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    override fun navigateToFinances() {
        val intent = Intent(this, IncomeExpensesActivity::class.java)
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

    override fun onDestroy() {
        removeMonthChangeListener(this)
        super.onDestroy()
    }
}