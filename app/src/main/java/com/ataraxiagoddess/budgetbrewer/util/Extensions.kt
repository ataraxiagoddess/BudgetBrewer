package com.ataraxiagoddess.budgetbrewer.util

import android.content.res.Resources
import com.ataraxiagoddess.budgetbrewer.R
import java.util.Locale

fun Double.toCurrencyDisplay(resources: Resources): String {
    return String.format(resources.getString(R.string.currency_display), CurrencyPrefs.currentSymbol, this)
}

fun Double.toCurrencyEdit(resources: Resources): String {
    return String.format(resources.getString(R.string.currency_edit), this)
}

fun Double.toPercentDisplay(resources: Resources): String {
    return String.format(resources.getString(R.string.percent_display), this)
}

fun Double.toCurrencyFormat(currency: String = CurrencyPrefs.currentSymbol, resources: Resources): String {
    val formatted = String.format(Locale.US, "%.2f", this)
    return String.format(resources.getString(R.string.currency_with_symbol), currency, formatted)
}