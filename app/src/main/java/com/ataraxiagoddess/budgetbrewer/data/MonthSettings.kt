/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "month_settings",
    indices = [Index(value = ["budgetId"], unique = true)]
)
data class MonthSettings(
    @PrimaryKey
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerialName("budget_id")
    val budgetId: String,
    @SerialName("month_start_amount")
    val monthStartAmount: Double,
    @SerialName("month_start_overridden")
    val monthStartOverridden: Boolean = false,
    @SerialName("tips_enabled")
    @ColumnInfo(defaultValue = "0")
    val tipsEnabled: Boolean = false,
    @SerialName("pay_frequency")
    @ColumnInfo(defaultValue = "'MONTHLY'")
    val payFrequency: String = "MONTHLY",
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
