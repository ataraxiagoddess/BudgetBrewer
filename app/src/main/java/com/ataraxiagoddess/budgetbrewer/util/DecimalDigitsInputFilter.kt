package com.ataraxiagoddess.budgetbrewer.util

import android.text.InputFilter
import android.text.Spanned

class DecimalDigitsInputFilter(private val digitsAfterZero: Int = 2) : InputFilter {

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

        if (builder.toString().matches(Regex("^\\d*\\.?\\d*$"))) {
            val parts = builder.toString().split(".")
            if (parts.size == 2 && parts[1].length > digitsAfterZero) {
                return ""
            }
            return null
        }
        return ""
    }
}