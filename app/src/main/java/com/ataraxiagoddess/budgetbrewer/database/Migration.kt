package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop old tables if they exist (to recover from any bad previous migration)
        db.execSQL("DROP TABLE IF EXISTS savings_transactions")
        db.execSQL("DROP TABLE IF EXISTS savings_buckets")

        // Create savings_buckets table matching the SavingsBucket entity
        db.execSQL("""
            CREATE TABLE savings_buckets (
                id TEXT NOT NULL PRIMARY KEY,
                budget_id TEXT NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                current_amount REAL NOT NULL DEFAULT 0.0,
                target_amount REAL,
                color_hex TEXT NOT NULL DEFAULT '#78b4e7',
                is_archived INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())

        // Create savings_transactions table matching the SavingsTransaction entity
        db.execSQL("""
            CREATE TABLE savings_transactions (
                id TEXT NOT NULL PRIMARY KEY,
                bucket_id TEXT NOT NULL,
                amount REAL NOT NULL,
                date INTEGER NOT NULL,
                type TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        // Create indices for performance
        db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_buckets_budget_id ON savings_buckets(budget_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_buckets_type ON savings_buckets(type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_transactions_bucket_id ON savings_transactions(bucket_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_transactions_date ON savings_transactions(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_transactions_type ON savings_transactions(type)")
    }
}