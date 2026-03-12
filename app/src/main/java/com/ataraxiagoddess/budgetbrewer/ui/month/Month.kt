package com.ataraxiagoddess.budgetbrewer.ui.month

import android.content.Context
import com.ataraxiagoddess.budgetbrewer.R
import java.util.Calendar
import java.util.Locale

data class Month(
    val year: Int,
    val month: Int // 1-12 (January = 1)
) {
    fun getDisplayName(context: Context): String {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
        return context.getString(R.string.month_year_format, monthName, year)
    }

    companion object {
        fun current(): Month {
            val calendar = Calendar.getInstance()
            return Month(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1
            )
        }
    }
}