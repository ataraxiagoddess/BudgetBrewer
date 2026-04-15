package com.ataraxiagoddess.budgetbrewer.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ataraxiagoddess.budgetbrewer.data.Allocation
import com.ataraxiagoddess.budgetbrewer.data.Budget
import com.ataraxiagoddess.budgetbrewer.data.DailyChecklist
import com.ataraxiagoddess.budgetbrewer.data.DailyIncomeAssignment
import com.ataraxiagoddess.budgetbrewer.data.Expense
import com.ataraxiagoddess.budgetbrewer.data.ExpenseCategory
import com.ataraxiagoddess.budgetbrewer.data.Income
import com.ataraxiagoddess.budgetbrewer.data.MonthSettings
import com.ataraxiagoddess.budgetbrewer.data.PendingSync
import com.ataraxiagoddess.budgetbrewer.data.SavingsBucket
import com.ataraxiagoddess.budgetbrewer.data.SavingsTransaction
import com.ataraxiagoddess.budgetbrewer.data.SpendingEntry

@Database(
    entities = [
        Budget::class,
        Income::class,
        ExpenseCategory::class,
        Expense::class,
        Allocation::class,
        DailyChecklist::class,
        SpendingEntry::class,
        MonthSettings::class,
        DailyIncomeAssignment::class,
        PendingSync::class,
        SavingsBucket::class,
        SavingsTransaction::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun budgetDao(): BudgetDao
    abstract fun incomeDao(): IncomeDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun allocationDao(): AllocationDao
    abstract fun dailyChecklistDao(): DailyChecklistDao
    abstract fun spendingEntryDao(): SpendingEntryDao
    abstract fun monthSettingsDao(): MonthSettingsDao
    abstract fun dailyIncomeAssignmentDao(): DailyIncomeAssignmentDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun savingsBucketDao(): SavingsBucketDao
    abstract fun savingsTransactionDao(): SavingsTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "budget_brewer.db"
                )
                    .addMigrations(Migration)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
