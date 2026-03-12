package com.ataraxiagoddess.budgetbrewer.database

import androidx.room.TypeConverter
import com.ataraxiagoddess.budgetbrewer.data.Frequency
import com.ataraxiagoddess.budgetbrewer.data.RecurrenceType

class Converters {
    @TypeConverter
    fun fromFrequency(frequency: Frequency): String = frequency.name

    @TypeConverter
    fun toFrequency(frequency: String): Frequency = Frequency.valueOf(frequency)

    @Suppress("unused")
    @TypeConverter
    fun fromRecurrenceType(type: RecurrenceType): String = type.name

    @Suppress("unused")
    @TypeConverter
    fun toRecurrenceType(name: String): RecurrenceType = RecurrenceType.valueOf(name)
}