package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "savings_transactions",
    foreignKeys = [
        ForeignKey(
            entity = SavingsBucket::class,
            parentColumns = ["id"],
            childColumns = ["bucketId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SavingsTransaction(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "bucket_id")
    val bucketId: Long,
    
    @ColumnInfo(name = "amount")
    val amount: Double,
    
    @ColumnInfo(name = "date")
    val date: Long,
    
    @ColumnInfo(name = "type")
    val type: SavingsTransactionType,
    
    @ColumnInfo(name = "description")
    val description: String
)

enum class SavingsTransactionType {
    ALLOCATION,
    DEDUCTION,
    WITHDRAWAL
}
