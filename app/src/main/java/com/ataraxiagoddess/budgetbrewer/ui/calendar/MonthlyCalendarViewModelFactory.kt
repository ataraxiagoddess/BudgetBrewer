package com.ataraxiagoddess.budgetbrewer.ui.calendar

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ataraxiagoddess.budgetbrewer.data.BudgetRepository
import kotlinx.coroutines.runBlocking
import java.util.Calendar

@Suppress("UNCHECKED_CAST")
class MonthlyCalendarViewModelFactory(
    private val repository: BudgetRepository,
    private val context: Context
) : ViewModelProvider.Factory {

    @SuppressLint("VisibleForTests")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val prefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
        val month = prefs.getInt("selected_month", Calendar.getInstance().get(Calendar.MONTH) + 1)
        val year = prefs.getInt("selected_year", Calendar.getInstance().get(Calendar.YEAR))

        val budgetId = runBlocking {
            repository.getOrCreateBudgetChain(month, year)
        }

        val savedStateHandle = SavedStateHandle(mapOf("budgetId" to budgetId))
        return MonthlyCalendarViewModel(repository, savedStateHandle, context.applicationContext) as T
    }
}