/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.ui.base

import com.ataraxiagoddess.budgetbrewer.ui.month.Month

fun interface MonthChangeListener {
    fun onMonthChanged(month: Month)
}
