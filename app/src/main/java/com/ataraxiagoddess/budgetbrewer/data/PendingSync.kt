package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "pending_sync")
data class PendingSync(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val operation: String,
    val table: String,
    val recordId: String,
    val userId: String,
    val timestamp: Long = System.currentTimeMillis()
)