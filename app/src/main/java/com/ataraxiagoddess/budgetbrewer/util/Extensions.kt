/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.util

import android.content.res.Resources
import com.ataraxiagoddess.budgetbrewer.R
import java.util.Locale

private fun localeFrom(resources: Resources): Locale {
    val locales = resources.configuration.locales
    return if (locales.isEmpty) Locale.getDefault() else locales[0]
}

fun Double.toCurrencyDisplay(resources: Resources): String = CurrencyPrefs.format(this, localeFrom(resources))

fun Double.toCurrencyEdit(resources: Resources): String = CurrencyPrefs.formatPlain(this, localeFrom(resources))

fun Double.toPercentDisplay(resources: Resources): String = String.format(resources.getString(R.string.percent_display), this)

fun Double.toCurrencyFormat(
    currency: String = CurrencyPrefs.currentCode,
    resources: Resources,
): String = CurrencyPrefs.formatWithCurrency(this, currency, localeFrom(resources))

fun String.toAmountOrNull(resources: Resources): Double? = CurrencyPrefs.parseAmount(this, localeFrom(resources))
