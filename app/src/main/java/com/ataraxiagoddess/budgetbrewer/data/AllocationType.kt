package com.ataraxiagoddess.budgetbrewer.data

sealed class AllocationType {
    object Savings : AllocationType()
    object Spending : AllocationType()

    val displayName: String
        get() = when (this) {
            Savings -> "Savings"
            Spending -> "Spending"
        }
}