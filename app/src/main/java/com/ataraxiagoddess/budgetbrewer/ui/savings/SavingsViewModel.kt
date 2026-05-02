package com.ataraxiagoddess.budgetbrewer.ui.savings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import com.ataraxiagoddess.budgetbrewer.data.SyncManager
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseViewModel
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.util.Constants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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

    /** Reactive available pool – recalculates automatically when allocations or transactions change */
    val availablePool: StateFlow<Double> = repository.getAvailableSavingsPool()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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
                repository.getActiveSavingsBuckets()
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
            // Validate against the provided pool (which is the current value at the time the dialog was shown)
            if (amount > availablePool && amount > 0) {
                _events.emit(SavingsUiEvent.ShowError("Not enough funds available"))
                return@safeLaunch
            }
            repository.distributeFunds(bucket, amount, budgetId)

            // Sync the updated bucket (the flow will recalculate the pool automatically)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                val updatedBucket = repository.getActiveSavingsBuckets().first().find { it.id == bucket.id }
                if (updatedBucket != null) {
                    SyncManager(appContext).uploadSavingsBucket(updatedBucket, userId)
                }
            }
            _events.emit(SavingsUiEvent.FundsDistributed)
        }
    }

    fun requestDistribute(bucket: SavingsBucket) {
        viewModelScope.launch {
            // Emit a DistributeRequest with the current pool value (reactive)
            _bucketEvents.emit(DistributeRequest(bucket, availablePool.value))
        }
    }

    fun archiveBucket(bucket: SavingsBucket) {
        safeLaunch(R.string.error_distribute) {
            repository.archiveBucket(bucket)

            // Sync the updated (archived) bucket
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                val updatedBucket = repository.getSavingsBucketById(bucket.id)
                if (updatedBucket != null) {
                    SyncManager(appContext).uploadSavingsBucket(updatedBucket, userId)
                }
            }
            _events.emit(SavingsUiEvent.BucketArchived)
        }
    }

    fun restoreBucket(bucket: SavingsBucket) {
        safeLaunch(R.string.error_restore) {
            repository.restoreBucket(bucket)

            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                val updatedBucket = repository.getSavingsBucketById(bucket.id)
                if (updatedBucket != null) {
                    SyncManager(appContext).uploadSavingsBucket(updatedBucket, userId)
                }
            }
            _events.emit(SavingsUiEvent.BucketRestored)
        }
    }

    fun editTransaction(transaction: SavingsTransaction, newAmount: Double) {
        safeLaunch(R.string.error_distribute) {
            repository.editTransactionAmount(transaction, newAmount)
            _events.emit(SavingsUiEvent.TransactionEdited)
        }
    }

    fun deleteTransaction(transaction: SavingsTransaction) {
        safeLaunch(R.string.error_distribute) {
            repository.deleteTransaction(transaction)
            _events.emit(SavingsUiEvent.TransactionDeleted)
        }
    }
}