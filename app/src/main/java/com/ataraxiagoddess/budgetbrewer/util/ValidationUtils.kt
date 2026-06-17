package com.ataraxiagoddess.budgetbrewer.util

import android.text.InputFilter

object ValidationUtils {

    // --- Length Limits ---
    const val MAX_LENGTH_PIN = 4
    const val MAX_LENGTH_NAME = 50
    const val MAX_LENGTH_NOTE = 250 // Generous limit for notes
    const val MAX_LENGTH_EMAIL = 100
    const val MAX_LENGTH_PASSWORD = 128

    // --- Financial Limits ---
    const val MAX_AMOUNT = 1_000_000_000.0 // 1 Billion
    const val MAX_RECURRENCE_DAYS = 365

    // --- Sanitization ---
    /**
     * Cleans a string by trimming whitespace and removing invisible control characters.
     */
    fun sanitizeString(input: String?): String {
        if (input == null) return ""
        return input.trim().replace(Regex("[\\x00-\\x1F\\x7F]"), "")
    }

    // --- Validation Functions ---
    
    fun isValidPin(pin: String): Boolean {
        return pin.length == MAX_LENGTH_PIN && pin.all { it.isDigit() }
    }

    fun isValidAmount(amountStr: String): Boolean {
        return amountStr.toDoubleOrNull()?.let { it in 0.0..MAX_AMOUNT } == true
    }

    fun isValidAmount(amount: Double): Boolean {
        return amount in 0.0..MAX_AMOUNT
    }

    fun isValidName(name: String, maxLength: Int = MAX_LENGTH_NAME): Boolean {
        val cleanName = sanitizeString(name)
        return cleanName.isNotEmpty() && cleanName.length <= maxLength
    }

    fun isValidNote(note: String): Boolean {
        val cleanNote = sanitizeString(note)
        return cleanNote.length <= MAX_LENGTH_NOTE
    }

    fun isValidRecurrenceDays(days: Int): Boolean {
        return days in 1..MAX_RECURRENCE_DAYS
    }

    // --- InputFilters for EditTexts ---
    
    /**
     * Returns an InputFilter that restricts the length of the input.
     */
    fun getLengthFilter(maxLength: Int): InputFilter {
        return InputFilter.LengthFilter(maxLength)
    }

    /**
     * Returns an InputFilter that only allows digits (0-9).
     * Useful for PIN fields.
     */
    fun getDigitsOnlyFilter(): InputFilter {
        return InputFilter { source, start, end, _, _, _ ->
            val filtered = source.subSequence(start, end).filter { it.isDigit() }
            if (filtered.length != end - start) filtered else null
        }
    }

    /**
     * Returns an InputFilter that blocks invisible control characters 
     * (like newlines, tabs, or null bytes) from being typed.
     */
    fun getControlCharactersBlockFilter(): InputFilter {
        return InputFilter { source, start, end, _, _, _ ->
            val sub = source.subSequence(start, end).toString()
            if (sub.contains(Regex("[\\x00-\\x1F\\x7F]"))) {
                sub.replace(Regex("[\\x00-\\x1F\\x7F]"), "")
            } else {
                null
            }
        }
    }
}
