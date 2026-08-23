/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.savings

sealed class SavingsUiEvent {
    object BucketAdded : SavingsUiEvent()

    object BucketUpdated : SavingsUiEvent()

    object BucketDeleted : SavingsUiEvent()

    object FundsDistributed : SavingsUiEvent()

    object BucketWithdrawn : SavingsUiEvent()

    object BucketArchived : SavingsUiEvent()

    object BucketRestored : SavingsUiEvent()

    object TransactionEdited : SavingsUiEvent()

    object TransactionDeleted : SavingsUiEvent()

    data class ShowError(
        val message: String,
    ) : SavingsUiEvent()

    data class ShowMessage(
        val message: String,
    ) : SavingsUiEvent()
}
