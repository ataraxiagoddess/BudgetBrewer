package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SyncManager
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseViewModel
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.util.Constants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SavingsViewModel(
    private val repository: BudgetRepository,
    private val savedStateHandle: SavedStateHandle,
    private val appContext: Context
) : BaseViewModel() {

    private var budgetId: String = savedStateHandle.get<String>("budgetId") ?: ""

    private val _uiState = MutableStateFlow<SavingsUiState>(SavingsUiState.Loading)
    val uiState: StateFlow<SavingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SavingsUiEvent>()
    val events = _events.asSharedFlow()

    private val _bucketEvents = MutableSharedFlow<DistributeRequest>()
    val bucketEvents = _bucketEvents.asSharedFlow()

    val availablePool: Double get() = 200.0 // placeholder for now

    init {
        loadData()
    }

    fun updateMonth(month: Month) {
        viewModelScope.launch {
            val newBudgetId = repository.getOrCreateBudgetChain(month.month, month.year)
            budgetId = newBudgetId
            savedStateHandle["budgetId"] = newBudgetId
            loadData()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = SavingsUiState.Loading
            try {
                repository.getSavingsBuckets()
                    .catch { e ->
                        _uiState.value = SavingsUiState.Error(e.message ?: "Unknown error")
                        emitError(R.string.error_load_data, e)
                    }
                    .collect { buckets ->
                        _uiState.value = SavingsUiState.Success(
                            buckets = buckets,
                            isEmpty = buckets.isEmpty(),
                            maxBucketsReached = buckets.size >= Constants.MAX_SAVINGS_BUCKETS
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = SavingsUiState.Error(e.message ?: "Unknown error")
                emitError(R.string.error_load_data, e)
            }
        }
    }

    fun createBucket(bucket: SavingsBucket) {
        safeLaunch(R.string.error_add_transaction) {
            repository.insertSavingsBucket(bucket)

            // Sync after insert
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadSavingsBucket(bucket, userId)
            }

            _events.emit(SavingsUiEvent.BucketAdded)
        }
    }

    fun editBucket(bucket: SavingsBucket) {
        safeLaunch(R.string.error_update_bucket) {
            repository.updateSavingsBucket(bucket)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadSavingsBucket(bucket, userId)
            }
            _events.emit(SavingsUiEvent.BucketUpdated)
        }
    }

    fun deleteBucket(bucket: SavingsBucket) {
        safeLaunch(R.string.error_delete_bucket) {
            repository.deleteSavingsBucket(bucket)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).deleteSavingsBucket(bucket.id, userId)
            }
            _events.emit(SavingsUiEvent.BucketDeleted)
        }
    }

    fun distributeFunds(bucket: SavingsBucket, amount: Double, availablePool: Double) {
        safeLaunch(R.string.error_distribute) {
            if (amount > availablePool && amount > 0) {
                _events.emit(SavingsUiEvent.ShowError("Not enough funds available"))
                return@safeLaunch
            }
            repository.distributeFunds(bucket, amount, budgetId)
            // Sync the new transaction and updated bucket
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                // Upload the updated bucket
                val updatedBucket = repository.getSavingsBuckets().first().find { it.id == bucket.id }
                if (updatedBucket != null) {
                    SyncManager(appContext).uploadSavingsBucket(updatedBucket, userId)
                }
                // Upload the transaction (simplified: we trust the repository to have dealt with it)
            }
            _events.emit(SavingsUiEvent.FundsDistributed)
        }
    }

    /**
     * Called by the Activity when the user taps "Distribute" on a bucket card.
     * Emits a DistributeRequest event so the Activity can show the dialog.
     */
    fun requestDistribute(bucket: SavingsBucket) {
        viewModelScope.launch {
            // Compute available pool (dummy: to be wired in Phase 4)
            val availablePool = 200.0 // placeholder
            _bucketEvents.emit(DistributeRequest(bucket, availablePool))
        }
    }
}