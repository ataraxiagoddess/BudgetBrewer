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
import kotlinx.coroutines.launch

class SavingsViewModel(
    private val repository: BudgetRepository,
    private val savedStateHandle: SavedStateHandle,
    private val appContext: Context
) : BaseViewModel() {
    private val _uiState = MutableStateFlow<SavingsUiState>(SavingsUiState.Loading)
    val uiState: StateFlow<SavingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SavingsUiEvent>()
    val events = _events.asSharedFlow()

    init {
        loadData()
    }

    fun updateMonth(month: Month) {
        viewModelScope.launch {
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
}