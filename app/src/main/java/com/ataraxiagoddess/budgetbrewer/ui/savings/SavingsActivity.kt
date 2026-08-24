/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.ataraxiagoddess.budgetbrewer.MainActivity
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.databinding.ActivitySavingsBinding
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.base.showBudgetBrewerDialog
import com.ataraxiagoddess.budgetbrewer.ui.calendar.MonthlyCalendarActivity
import com.ataraxiagoddess.budgetbrewer.ui.expenses.MonthlyExpenseListActivity
import com.ataraxiagoddess.budgetbrewer.ui.finances.IncomeExpensesActivity
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity
import com.ataraxiagoddess.budgetbrewer.ui.spending.SpendingActivity
import com.ataraxiagoddess.budgetbrewer.util.GridSpacingItemDecoration
import com.ataraxiagoddess.budgetbrewer.util.toCurrencyDisplay
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
        adapter =
            SavingsBucketAdapter(
                onDistributeClick = { bucket -> viewModel.requestDistribute(bucket) },
                onDeductClick = { bucket -> showDeductDialog(bucket) },
                onWithdrawClick = { bucket -> showWithdrawDialog(bucket) },
                onEditClick = { bucket -> showEditBucketDialog(bucket) },
                onDeleteClick = { bucket -> showDeleteConfirmationDialog(bucket) },
                onCardClick = { bucket -> showHistoryDialog(bucket) }
            )
        val isTablet = resources.getBoolean(R.bool.is_tablet)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        when {
            isTablet -> {
                val gridLayoutManager = GridLayoutManager(this, 2)
                binding.recyclerViewBuckets.layoutManager = gridLayoutManager
                val spacing = resources.getDimensionPixelSize(R.dimen.expenses_grid_spacing)
                while (binding.recyclerViewBuckets.itemDecorationCount > 0) {
                    binding.recyclerViewBuckets.removeItemDecorationAt(0)
                }
                binding.recyclerViewBuckets.addItemDecoration(
                    GridSpacingItemDecoration(2, spacing, true)
                )
            }
            !isTablet && isLandscape -> {
                val gridLayoutManager = GridLayoutManager(this, 2)
                binding.recyclerViewBuckets.layoutManager = gridLayoutManager
                val spacing = resources.getDimensionPixelSize(R.dimen.expenses_grid_spacing)
                while (binding.recyclerViewBuckets.itemDecorationCount > 0) {
                    binding.recyclerViewBuckets.removeItemDecorationAt(0)
                }
                binding.recyclerViewBuckets.addItemDecoration(
                    GridSpacingItemDecoration(2, spacing, true)
                )
            }
            else -> {
                binding.recyclerViewBuckets.layoutManager = LinearLayoutManager(this)
            }
        }
        binding.recyclerViewBuckets.adapter = adapter

        binding.fabAddBucket.setOnClickListener {
            showCreateBucketDialog()
        }
        binding.fabAddBucket.contentDescription = getString(R.string.add_bucket)

        // Click listener for Add Bucket button (in empty state)
        binding.emptyStateContainer.buttonAddBucket.setOnClickListener {
            showCreateBucketDialog()
        }

        // Observe available pool and update the card
        lifecycleScope.launch {
            viewModel.availablePool.collect { pool ->
                binding.tvAvailablePoolAmount.text = pool.toCurrencyDisplay(resources)
            }
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
                        adapter.transactionCounts = state.transactionCounts
                        // Use notifyItemRangeChanged instead of notifyDataSetChanged
                        // This is a more specific change event that triggers a rebind
                        // of the visible items to pick up the new transactionCounts
                        if (adapter.itemCount > 0) {
                            adapter.notifyItemRangeChanged(0, adapter.itemCount)
                        }
                        if (state.isEmpty) {
                            showEmptyState()
                        } else {
                            showBucketList(state.buckets)
                        }
                        binding.fabAddBucket.isEnabled = !state.maxBucketsReached
                        binding.fabAddBucket.backgroundTintList =
                            if (state.maxBucketsReached) {
                                ColorStateList.valueOf(
                                    ContextCompat.getColor(
                                        this@SavingsActivity,
                                        R.color.text_disabled
                                    )
                                )
                            } else {
                                ColorStateList.valueOf(
                                    ContextCompat.getColor(
                                        this@SavingsActivity,
                                        R.color.btn_on_main
                                    )
                                )
                            }
                    }
                    is SavingsUiState.Error -> {
                        hideLoading()
                        showSnackbar(state.message)
                    }
                }
            }
        }

        // Observe one-shot events
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is SavingsUiEvent.BucketAdded -> showSnackbar(getString(R.string.bucket_added))
                    is SavingsUiEvent.BucketUpdated -> showSnackbar(
                        getString(R.string.bucket_updated)
                    )
                    is SavingsUiEvent.BucketDeleted -> showSnackbar(
                        getString(R.string.bucket_deleted)
                    )
                    is SavingsUiEvent.FundsDistributed -> showSnackbar(
                        getString(R.string.funds_distributed)
                    )
                    is SavingsUiEvent.ShowError -> {
                        showSnackbar(event.message)
                    }
                    is SavingsUiEvent.ShowMessage -> {
                        showSnackbar(event.message)
                    }
                    is SavingsUiEvent.BucketWithdrawn -> showSnackbar(
                        getString(R.string.funds_withdrawn)
                    )
                    is SavingsUiEvent.BucketArchived -> showSnackbar(
                        getString(R.string.bucket_archived)
                    )
                    is SavingsUiEvent.BucketRestored -> showSnackbar(
                        getString(R.string.bucket_restored)
                    )
                    is SavingsUiEvent.TransactionEdited -> showSnackbar(
                        getString(R.string.transaction_edited)
                    )
                    is SavingsUiEvent.TransactionDeleted -> showSnackbar(
                        getString(R.string.transaction_deleted)
                    )
                }
            }
        }

        // Observe distribute requests
        lifecycleScope.launch {
            viewModel.bucketEvents.collect { request ->
                showDistributeDialog(request.bucket, request.availablePool)
            }
        }

        // Listen for month changes from BaseActivity
        addMonthChangeListener { month -> viewModel.updateMonth(month) }
    }

    private fun showCreateBucketDialog() {
        val dialog =
            CreateBucketDialogFragment(
                onBucketCreated = { bucket -> viewModel.createBucket(bucket) },
                onShowSnackbar = { message -> this.showSnackbar(message) }
            )
        dialog.show(supportFragmentManager, "CreateBucketDialog")
    }

    private fun showDeductDialog(bucket: SavingsBucket) {
        val dialog =
            DistributeDialogFragment(
                bucket = bucket,
                availablePool = 0.0, // not used
                isDeduction = true,
                onDistribute = { amount ->
                    viewModel.distributeFunds(bucket, -amount, viewModel.availablePool.value)
                }
            )
        dialog.show(supportFragmentManager, "DeductDialog")
    }

    private fun showEditTransactionDialog(transaction: SavingsTransaction, bucket: SavingsBucket) {
        val originalAmount = kotlin.math.abs(transaction.amount)
        val maxAllowed =
            if (transaction.amount > 0) {
                // Allocation: max = available pool + original amount
                viewModel.availablePool.value + originalAmount
            } else {
                // Deduction: max = bucket current amount + original amount
                bucket.current_amount + originalAmount
            }
        val dialog =
            EditTransactionDialogFragment(
                transaction = transaction,
                maxAllowed = maxAllowed,
                onSave = { newAmount -> viewModel.editTransaction(transaction, newAmount) }
            )
        dialog.show(supportFragmentManager, "EditTransactionDialog")
    }

    private fun showWithdrawDialog(bucket: SavingsBucket) {
        // Step 1: Confirmation dialog
        showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.withdraw_title),
            message = getString(
                R.string.withdraw_confirmation,
                bucket.name,
                bucket.current_amount.toCurrencyDisplay(resources)
            ),
            positiveButton = getString(R.string.withdraw),
            negativeButton = getString(R.string.cancel),
            onPositive = {
                viewModel.archiveBucket(bucket)
            }
        ).show()
    }

    private fun showEditBucketDialog(bucket: SavingsBucket) {
        val dialog =
            EditBucketDialogFragment(
                existingBucket = bucket,
                onBucketUpdated = { updated -> viewModel.editBucket(updated) },
                onShowSnackbar = { message -> showSnackbar(message) }
            )
        dialog.show(supportFragmentManager, "EditBucketDialog")
    }

    private fun showDistributeDialog(bucket: SavingsBucket, availablePool: Double) {
        val dialog =
            DistributeDialogFragment(
                bucket = bucket,
                availablePool = availablePool,
                isDeduction = false,
                onDistribute = { amount ->
                    viewModel.distributeFunds(bucket, amount, availablePool)
                }
            )
        dialog.show(supportFragmentManager, "DistributeDialog")
    }

    private fun showHistoryDialog(bucket: SavingsBucket) {
        val db = AppDatabase.getDatabase(this)
        val repository = BudgetRepository(db)
        val dialog =
            BucketHistoryDialogFragment(
                bucketName = bucket.name,
                bucketId = bucket.id,
                repository = repository,
                isArchived = false,
                onEditTransaction = { tx -> showEditTransactionDialog(tx, bucket) },
                onDeleteTransaction = { tx -> viewModel.deleteTransaction(tx) }
            )
        dialog.show(supportFragmentManager, "BucketHistory")
    }

    private fun showDeleteConfirmationDialog(bucket: SavingsBucket) {
        showBudgetBrewerDialog(
            inflater = layoutInflater,
            context = this,
            title = getString(R.string.delete_bucket_title),
            message = getString(R.string.delete_bucket_message, bucket.name),
            positiveButton = getString(R.string.delete),
            negativeButton = getString(R.string.cancel),
            onPositive = {
                viewModel.deleteBucket(bucket)
            }
        ).show()
    }

    private fun showEmptyState() {
        binding.emptyStateContainer.root.visibility = View.VISIBLE
        binding.recyclerViewBuckets.visibility = View.GONE
        binding.fabAddBucket.visibility = View.GONE
    }

    private fun showBucketList(buckets: List<SavingsBucket>) {
        binding.emptyStateContainer.root.visibility = View.GONE
        binding.recyclerViewBuckets.visibility = View.VISIBLE
        binding.fabAddBucket.visibility = View.VISIBLE
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
