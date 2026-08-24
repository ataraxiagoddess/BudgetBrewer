/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository

class ArchivedBucketsViewModelFactory(private val repository: BudgetRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ArchivedBucketsViewModel(repository) as T
}
