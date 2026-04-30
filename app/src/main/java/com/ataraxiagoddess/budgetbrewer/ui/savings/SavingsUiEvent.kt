package com.ataraxiagoddess.budgetbrewer.ui.savings

sealed class SavingsUiEvent {
    object BucketAdded : SavingsUiEvent()
    object BucketUpdated : SavingsUiEvent()
    object BucketDeleted : SavingsUiEvent()
    object FundsDistributed : SavingsUiEvent()
    data class ShowError(val message: String) : SavingsUiEvent()
}