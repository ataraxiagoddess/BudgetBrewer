/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.util

import android.content.Context
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.max

object CurrencyPrefs {
    private const val PREFS_NAME = "settings"
    private const val KEY_CURRENCY = "currency"

    var currentCode: String = "USD"
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CURRENCY, "USD") ?: "USD"
        currentCode = extractCode(saved)
    }

    fun updateCurrency(prefValue: String) {
        currentCode = extractCode(prefValue)
    }

    val currentCurrency: Currency
        get() = Currency.getInstance(currentCode)

    val currentFractionDigits: Int
        get() = max(0, currentCurrency.defaultFractionDigits)

    fun extractCode(value: String): String {
        val trimmed = value.trim()
        val codeMatch = Regex("\\(([A-Z]{3})\\)").find(trimmed)?.groupValues?.get(1)
        if (codeMatch != null) return codeMatch

        val upper = trimmed.uppercase(Locale.US)
        if (Regex("^[A-Z]{3}$").matches(upper)) return upper

        return symbolToCode(trimmed)
    }

    fun format(
        amount: Double,
        locale: Locale,
    ): String = formatWithCode(amount, currentCode, locale)

    fun formatWithCurrency(
        amount: Double,
        currencyValue: String,
        locale: Locale,
    ): String {
        val code = extractCode(currencyValue)
        return formatWithCode(amount, code, locale)
    }

    fun formatPlain(
        amount: Double,
        locale: Locale,
    ): String {
        val formatter = NumberFormat.getNumberInstance(locale)
        formatter.minimumFractionDigits = currentFractionDigits
        formatter.maximumFractionDigits = currentFractionDigits
        formatter.isGroupingUsed = false
        return formatter.format(amount)
    }

    fun parseAmount(
        input: String,
        locale: Locale,
    ): Double? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val formatter = NumberFormat.getNumberInstance(locale)
        val parsed = runCatching { formatter.parse(trimmed) }.getOrNull()
        if (parsed != null) return parsed.toDouble()
        val fallback = runCatching { NumberFormat.getNumberInstance(Locale.US).parse(trimmed) }.getOrNull()
        return fallback?.toDouble()
    }

    fun decimalSeparators(locale: Locale): Set<Char> {
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return if (separator == '.') setOf('.') else setOf(separator, '.')
    }

    private fun symbolToCode(symbol: String): String =
        when (symbol) {
            "$" -> "USD"
            "€" -> "EUR"
            "£" -> "GBP"
            "¥" -> "JPY"
            "₹" -> "INR"
            "₱" -> "PHP"
            "R$" -> "BRL"
            else -> "USD"
        }

    private fun formatWithCode(
        amount: Double,
        code: String,
        locale: Locale,
    ): String {
        val currency = Currency.getInstance(code)
        val formatter = NumberFormat.getCurrencyInstance(locale)
        val digits = max(0, currency.defaultFractionDigits)
        formatter.currency = currency
        formatter.minimumFractionDigits = digits
        formatter.maximumFractionDigits = digits
        return formatter.format(amount)
    }
}
