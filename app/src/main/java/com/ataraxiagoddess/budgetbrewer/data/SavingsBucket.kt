/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

@file:Suppress("PropertyName")
package com.ataraxiagoddess.budgetbrewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "savings_buckets",
    indices = [
        Index(value = ["type"])
    ]
)
data class SavingsBucket(
    @PrimaryKey
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerialName("name")
    val name: String,

    @SerialName("type")
    val type: SavingsBucketType,

    @SerialName("current_amount")
    val current_amount: Double = 0.0,

    @SerialName("target_amount")
    val target_amount: Double?,

    @SerialName("color_hex")
    val color_hex: String = "#78b4e7",

    @SerialName("is_archived")
    val is_archived: Boolean = false,

    @SerialName("created_at")
    val created_at: Long = System.currentTimeMillis(),

    @SerialName("updated_at")
    val updated_at: Long = System.currentTimeMillis()
)

@Serializable
enum class SavingsBucketType {
    GOAL,
    GROWTH
}
