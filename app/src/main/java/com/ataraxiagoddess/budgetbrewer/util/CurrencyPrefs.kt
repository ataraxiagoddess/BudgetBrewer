package com.ataraxiagoddess.budgetbrewer.util

import android.content.Context

object CurrencyPrefs {
    private const val PREFS_NAME = "settings"
    private const val KEY_CURRENCY = "currency"

    var currentSymbol: String = "$"
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentSymbol = extractSymbol(prefs.getString(KEY_CURRENCY, "$ (USD)") ?: "$ (USD)")
    }

    fun updateSymbol(prefValue: String) {
        currentSymbol = extractSymbol(prefValue)
    }

    private fun extractSymbol(fullString: String): String {
        return when {
            fullString.startsWith("$") -> "$"
            fullString.startsWith("€") -> "€"
            fullString.startsWith("£") -> "£"
            fullString.startsWith("¥") -> "¥"
            fullString.startsWith("₹") -> "₹"
            fullString.startsWith("₱") -> "₱"
            fullString.startsWith("R$") -> "R$"
            else -> "$"
        }
    }
}