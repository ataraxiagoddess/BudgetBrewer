/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.util

import android.text.InputFilter
import android.text.Spanned

class DecimalDigitsInputFilter(
    private val digitsAfterZero: Int = 2,
    private val decimalSeparators: Set<Char> = setOf('.')
) : InputFilter {
    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val builder = StringBuilder(dest)
        builder.replace(dstart, dend, source.subSequence(start, end).toString())

        val normalized =
            builder
                .toString()
                .map { ch ->
                    if (decimalSeparators.contains(ch)) '.' else ch
                }.joinToString("")

        if (!normalized.matches(Regex("^\\d*\\.?\\d*$"))) {
            return ""
        }

        val parts = normalized.split(".")
        if (parts.size == 2 && parts[1].length > digitsAfterZero) {
            return ""
        }
        return null
    }
}
