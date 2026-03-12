package com.ataraxiagoddess.budgetbrewer.ui.spending

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.R
import com.ataraxiagoddess.budgetbrewer.data.Allocation
import com.ataraxiagoddess.budgetbrewer.data.AuthManager
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SpendingEntry
import com.ataraxiagoddess.budgetbrewer.data.SyncManager
import com.ataraxiagoddess.budgetbrewer.ui.base.BaseViewModel
import com.ataraxiagoddess.budgetbrewer.ui.finances.UiEvent
import com.ataraxiagoddess.budgetbrewer.ui.month.Month
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SpendingViewModel(
    private val repository: BudgetRepository,
    private val savedStateHandle: SavedStateHandle,
    private val appContext: Context
) : BaseViewModel() {

    private var budgetId: String = savedStateHandle.get<String>("budgetId") ?: ""

    private val _uiState = MutableStateFlow<SpendingUiState>(SpendingUiState.Loading)
    val uiState: StateFlow<SpendingUiState> = _uiState.asStateFlow()

    data class SpendingUiData(
        val entries: List<SpendingEntry> = emptyList(),
        val allocation: Allocation? = null,
        val remaining: Double = 0.0
    )

    sealed class SpendingUiState {
        object Loading : SpendingUiState()
        data class Success(val data: SpendingUiData) : SpendingUiState()
        data class Error(val message: String) : SpendingUiState()
    }

    fun updateMonth(month: Month) {
        viewModelScope.launch {
            val newBudgetId = repository.getOrCreateBudgetChain(month.month, month.year)
            budgetId = newBudgetId
            savedStateHandle["budgetId"] = newBudgetId
            loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = SpendingUiState.Loading
            try {
                combine(
                    repository.getSpendingEntriesForBudget(budgetId),
                    repository.getAllocationForBudget(budgetId)
                ) { entries, allocation ->
                    val totalSpent = entries.sumOf { it.amount }
                    val spendingAmount = allocation?.spendingAmount ?: 0.0
                    val remaining = spendingAmount - totalSpent
                    SpendingUiData(entries, allocation, remaining)
                }.collect { data ->
                    _uiState.value = SpendingUiState.Success(data)
                }
            } catch (e: Exception) {
                _uiState.value = SpendingUiState.Error("Failed to load transactions: ${e.message}")
                emitError(R.string.error_load_transactions, e)
            }
        }
    }

    fun addEntry(date: Long, source: String, amount: Double) {
        safeLaunch(R.string.error_add_transaction) {
            val entry = SpendingEntry(
                budgetId = budgetId,
                date = date,
                source = source,
                amount = amount
            )
            repository.insertSpendingEntry(entry)
            // Sync after insert
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadSpendingEntry(entry, userId)
            }
            emitSuccess(UiEvent.SpendingAdded)
            loadData()
        }
    }

    fun updateEntry(entry: SpendingEntry) {
        safeLaunch(R.string.error_update_transaction) {
            repository.updateSpendingEntry(entry)
            // Sync after update
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).uploadSpendingEntry(entry, userId)
            }
            emitSuccess(UiEvent.SpendingUpdated)
            loadData()
        }
    }

    fun deleteEntry(entry: SpendingEntry) {
        safeLaunch(R.string.error_delete_transaction) {
            repository.deleteSpendingEntry(entry)
            // Sync after delete
            val userId = AuthManager.getUserId(appContext)
            if (userId != null) {
                SyncManager(appContext).deleteSpendingEntry(entry.id, userId)
            }
            emitSuccess(UiEvent.SpendingDeleted)
            loadData()
        }
    }
}