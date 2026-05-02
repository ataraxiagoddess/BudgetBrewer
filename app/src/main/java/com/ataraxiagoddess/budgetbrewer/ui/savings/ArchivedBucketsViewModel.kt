package com.ataraxiagoddess.budgetbrewer.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArchivedBucketsViewModel(
    private val repository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArchivedBucketsUiState>(ArchivedBucketsUiState.Loading)
    val uiState: StateFlow<ArchivedBucketsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = ArchivedBucketsUiState.Loading
            try {
                repository.getArchivedSavingsBuckets().collect { buckets ->
                    _uiState.value = ArchivedBucketsUiState.Success(buckets)
                }
            } catch (e: Exception) {
                _uiState.value = ArchivedBucketsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun restoreBucket(bucket: SavingsBucket) {
        viewModelScope.launch {
            repository.restoreBucket(bucket)
        }
    }

    fun deleteBucket(bucket: SavingsBucket) {
        viewModelScope.launch {
            repository.deleteSavingsBucket(bucket)
        }
    }
}

sealed class ArchivedBucketsUiState {
    object Loading : ArchivedBucketsUiState()
    data class Success(val buckets: List<SavingsBucket>) : ArchivedBucketsUiState()
    data class Error(val message: String) : ArchivedBucketsUiState()
}