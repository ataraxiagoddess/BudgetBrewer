package com.ataraxiagoddess.budgetbrewer.ui.savings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import com.ataraxiagoddess.budgetbrewer.util.Constants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavingsViewModel(
    private val repository: BudgetRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Replace with the actual active budget ID (from a session/preferences)
    private var budgetId: String = savedStateHandle.get<String>("budgetId") ?: ""

    private val _uiState = MutableStateFlow<SavingsUiState>(SavingsUiState.Loading)
    /**
     * Observes the list of savings buckets for the current budget.
     * The flow automatically updates when Room data changes,
     * so there’s no need to manually reload after inserting a bucket.
     */
    val uiState: StateFlow<SavingsUiState> = repository
        .getSavingsBucketsForBudget(budgetId)
        .map { buckets ->
            SavingsUiState.Success(
                buckets = buckets,
                isEmpty = buckets.isEmpty()
            ) as SavingsUiState
        }
        .catch { e ->
            emit(SavingsUiState.Error(e.message ?: "Unknown error"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SavingsUiState.Loading
        )

    private val _events = MutableSharedFlow<SavingsUiEvent>()
    val events = _events.asSharedFlow()

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
                repository.getSavingsBucketsForBudget(budgetId)
                    .catch { e ->
                        _uiState.value = SavingsUiState.Error(e.message ?: "Unknown error")
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
            }
        }
    }

    /**
     * Creates a new savings bucket. The repository will handle persistence;
     * the UI state updates automatically through the Room flow.
     */
    fun createBucket(bucket: SavingsBucket) {
        viewModelScope.launch {
            try {
                repository.insertSavingsBucket(bucket)
                _events.emit(SavingsUiEvent.BucketAdded)
            } catch (e: Exception) {
                _events.emit(SavingsUiEvent.ShowError(e.message ?: "Failed to create bucket"))
            }
        }
    }
}