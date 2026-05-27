package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_1_2 : Migration(1, 2) {
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

object Migration_2_3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop the index on budget_id first
        db.execSQL("DROP INDEX IF EXISTS index_savings_buckets_budget_id")

        // Create a new table without the budget_id column
        db.execSQL("""
            CREATE TABLE savings_buckets_new (
                id TEXT NOT NULL PRIMARY KEY,
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

        // Copy existing data
        db.execSQL("""
            INSERT INTO savings_buckets_new (id, name, type, current_amount, target_amount, color_hex, is_archived, created_at, updated_at)
            SELECT id, name, type, current_amount, target_amount, color_hex, is_archived, created_at, updated_at
            FROM savings_buckets
        """.trimIndent())

        // Drop old table and rename new one
        db.execSQL("DROP TABLE savings_buckets")
        db.execSQL("ALTER TABLE savings_buckets_new RENAME TO savings_buckets")

        // Recreate index on type
        db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_buckets_type ON savings_buckets(type)")
    }
}

object Migration_3_4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add tag and note columns to spending_entries table
        db.execSQL("ALTER TABLE spending_entries ADD COLUMN tag TEXT")
        db.execSQL("ALTER TABLE spending_entries ADD COLUMN note TEXT")
    }
}
