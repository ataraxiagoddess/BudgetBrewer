package com.ataraxiagoddess.budgetbrewer.util

import java.text.SimpleDateFormat
import java.util.Locale

object Constants {
    const val EPSILON = 0.001
    const val MAX_TIPS = 6
    const val MAX_CATEGORIES = 20
    const val MAX_EXPENSES_PER_CATEGORY = 20

    object DateFormats {
        val FULL = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)
        val SHORT = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    }
}

val FULL = Constants.DateFormats.FULL
val SHORT = Constants.DateFormats.SHORT