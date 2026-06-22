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
import kotlinx.coroutines.flow.combine
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
            val (newBudgetId, _) = repository.getOrCreateBudgetChain(month.month, month.year)
            budgetId = newBudgetId
            savedStateHandle["budgetId"] = newBudgetId
            loadData()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = SavingsUiState.Loading
            try {
                combine(
                    repository.getActiveSavingsBuckets(),
                    repository.getAllSavingsTransactionsFlow()
                ) { buckets, transactions ->
                    val transactionCounts = transactions.groupingBy { it.bucket_id }.eachCount()
                    Pair(buckets, transactionCounts)
                }
                    .catch { e ->
                        _uiState.value = SavingsUiState.Error(e.message ?: "Unknown error")
                        emitError(R.string.error_load_data, e)
                    }
                    .collect { (buckets, transactionCounts) ->
                        _uiState.value = SavingsUiState.Success(
                            buckets = buckets,
                            isEmpty = buckets.isEmpty(),
                            maxBucketsReached = buckets.size >= Constants.MAX_SAVINGS_BUCKETS,
                            transactionCounts = transactionCounts
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
            val transaction = repository.distributeFunds(bucket, amount)

            // Sync the updated bucket (the flow will recalculate the pool automatically)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                val updatedBucket = repository.getActiveSavingsBuckets().first().find { it.id == bucket.id }
                if (updatedBucket != null) {
                    SyncManager(appContext).uploadSavingsBucket(updatedBucket, userId)
                }
                SyncManager(appContext).uploadSavingsTransaction(transaction, userId)
            }

            if (amount < 0) {
                _events.emit(SavingsUiEvent.ShowMessage("Funds removed from bucket and added back to pool"))
            } else {
                _events.emit(SavingsUiEvent.FundsDistributed)
            }
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

    fun editTransaction(transaction: SavingsTransaction, newAmount: Double) {
        safeLaunch(R.string.error_distribute) {
            repository.editTransactionAmount(transaction, newAmount)
            val updatedTransaction = transaction.copy(amount = newAmount)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadSavingsTransaction(updatedTransaction, userId)
            }
            _events.emit(SavingsUiEvent.TransactionEdited)
        }
    }

    fun deleteTransaction(transaction: SavingsTransaction) {
        safeLaunch(R.string.error_distribute) {
            repository.deleteTransaction(transaction)
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).deleteSavingsTransaction(transaction.id, userId)
            }
            _events.emit(SavingsUiEvent.TransactionDeleted)
        }
    }
}