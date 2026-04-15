package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create savings_buckets table
        database.execSQL("""
            CREATE TABLE savings_buckets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                current_amount REAL NOT NULL DEFAULT 0.0,
                target_amount REAL NOT NULL DEFAULT 0.0,
                color TEXT NOT NULL,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
            )
        """.trimIndent())

        // Create savings_transactions table
        database.execSQL("""
            CREATE TABLE savings_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                bucket_id INTEGER NOT NULL,
                amount REAL NOT NULL,
                date INTEGER NOT NULL,
                type TEXT NOT NULL,
                description TEXT NOT NULL,
                FOREIGN KEY (bucket_id) REFERENCES savings_buckets(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Create indexes for better query performance
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_savings_buckets_type ON savings_buckets(type)
        """.trimIndent())

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_savings_transactions_bucket_id ON savings_transactions(bucket_id)
        """.trimIndent())

        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_savings_transactions_date ON savings_transactions(date)
        """.trimIndent())
    }
}
