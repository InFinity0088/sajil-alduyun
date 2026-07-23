package com.sajilalduyun.app.database


import androidx.room.TypeConverter
import java.util.Date

// Room database only understands numbers and text.
// These two functions teach it how to handle Dates.
class Converters {

    // Converts a Date to a Long number for storage
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }

    // Converts a Long number back to a Date when reading
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
}