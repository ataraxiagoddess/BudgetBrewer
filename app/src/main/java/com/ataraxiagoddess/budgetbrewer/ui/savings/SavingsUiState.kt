package com.ataraxiagoddess.budgetbrewer.ui.savings

import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket

data class DistributeRequest(
    val bucket: SavingsBucket,
    val availablePool: Double
)
sealed class SavingsUiState {
    object Loading : SavingsUiState()
    data class Success(
        val buckets: List<SavingsBucket>,
        val isEmpty: Boolean,
        val maxBucketsReached: Boolean = false
    ) : SavingsUiState()
    data class Error(val message: String) : SavingsUiState()
}