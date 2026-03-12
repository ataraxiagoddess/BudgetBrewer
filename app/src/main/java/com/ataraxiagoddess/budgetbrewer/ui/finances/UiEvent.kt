package com.ataraxiagoddess.budgetbrewer.ui.finances

import androidx.annotation.StringRes

/**
 * Sealed class representing UI events that should be shown to the user
 * Using StringRes allows the Activity to resolve the actual string from resources
 */
sealed class UiEvent {
    // Simple success messages (no parameters)
    object IncomeAdded : UiEvent()
    object IncomeUpdated : UiEvent()
    object IncomeDeleted : UiEvent()

    object TipAdded : UiEvent()
    object TipUpdated : UiEvent()
    object TipDeleted : UiEvent()

    object CategoryAdded : UiEvent()
    object CategoryUpdated : UiEvent()
    object CategoryDeleted : UiEvent()

    object ExpenseAdded : UiEvent()
    object ExpenseUpdated : UiEvent()
    object ExpenseDeleted : UiEvent()

    object SpendingAdded : UiEvent()
    object SpendingUpdated : UiEvent()
    object SpendingDeleted : UiEvent()


    // Messages with string resources (for localized text)
    data class ShowMessage(@param:StringRes val messageResId: Int) : UiEvent()

    // Error messages that may include dynamic content
    data class ShowError(
        @param:StringRes
        val errorResId: Int,
        val errorMessage: String? = null
    ) : UiEvent()
}