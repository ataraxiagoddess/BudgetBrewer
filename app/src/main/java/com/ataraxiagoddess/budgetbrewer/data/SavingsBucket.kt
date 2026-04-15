package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "savings_buckets")
data class SavingsBucket(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "type")
    val type: SavingsBucketType,
    
    @ColumnInfo(name = "current_amount")
    val currentAmount: Double = 0.0,
    
    @ColumnInfo(name = "target_amount")
    val targetAmount: Double = 0.0,
    
    @ColumnInfo(name = "color")
    val color: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

enum class SavingsBucketType {
    GOAL,
    GROWTH
}
