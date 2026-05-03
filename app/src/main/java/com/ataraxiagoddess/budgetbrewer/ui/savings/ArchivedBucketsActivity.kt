package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.database.AppDatabase
import com.ataraxiagoddess.budgetbrewer.databinding.ActivityArchivedBucketsBinding
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseActivity
import com.ataraxiagoddess.budgetbrewer.ui.navigation.NavDestination
import com.ataraxiagoddess.budgetbrewer.ui.settings.SettingsActivity
import com.ataraxiagoddess.budgetbrewer.util.GridSpacingItemDecoration
import kotlinx.coroutines.launch

class ArchivedBucketsActivity : BaseActivity() {

    override val currentNavDestination: NavDestination
        get() = NavDestination.SETTINGS

    private lateinit var binding: ActivityArchivedBucketsBinding
    private val viewModel: ArchivedBucketsViewModel by viewModels {
        ArchivedBucketsViewModelFactory(BudgetRepository(AppDatabase.getDatabase(this)))
    }
    private lateinit var adapter: ArchivedBucketsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArchivedBucketsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideNavigation()

        binding.btnBack.setOnClickListener {
            finish()
        }

        adapter = ArchivedBucketsAdapter(
            onRestoreClick = { bucket -> viewModel.restoreBucket(bucket) },
            onDeleteClick = { bucket -> viewModel.deleteBucket(bucket) },
            onCardClick = { bucket -> showHistoryDialog(bucket) }
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
                binding.recyclerView.addItemDecoration(
                    GridSpacingItemDecoration(2, spacing, true)
                )
            }
            !isTablet && isLandscape -> {
                val gridLayoutManager = GridLayoutManager(this, 2)
                binding.recyclerView.layoutManager = gridLayoutManager
                val spacing = resources.getDimensionPixelSize(R.dimen.expenses_grid_spacing)
                while (binding.recyclerView.itemDecorationCount > 0) {
                    binding.recyclerView.removeItemDecorationAt(0)
                }
                binding.recyclerView.addItemDecoration(
                    GridSpacingItemDecoration(2, spacing, true)
                )
            }
            else -> {
                binding.recyclerView.layoutManager = LinearLayoutManager(this)
            }
        }
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is ArchivedBucketsUiState.Success -> {
                        adapter.submitList(state.buckets)
                        binding.emptyView.visibility = if (state.buckets.isEmpty()) View.VISIBLE else View.GONE
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showHistoryDialog(bucket: SavingsBucket) {
        val db = AppDatabase.getDatabase(this)
        val repository = BudgetRepository(db)
        // No edit/delete callbacks → read-only
        val dialog = BucketHistoryDialogFragment(
            bucketName = bucket.name,
            bucketId = bucket.id,
            repository = repository,
            isArchived = true
        )
        dialog.show(supportFragmentManager, "BucketHistory")
    }

    override fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java),
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }
}