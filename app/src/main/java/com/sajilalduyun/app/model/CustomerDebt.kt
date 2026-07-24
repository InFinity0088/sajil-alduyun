package com.sajilalduyun.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

enum class DebtStatus {
    PENDING,
    APPROVED,
    LOCKED
}

// @Entity tells Room: "make a table for this"
// tableName = what the table is called in the database
@Entity(tableName = "debts")
data class CustomerDebt(
    @PrimaryKey // This field is the unique identifier — no two debts share the same ID
    val id: String,
    val customerName: String,
    var amount: Double,
    val planId: Long,                // FK to plans table (customizable plan)
    val maxLimit: Double,            // Snapshot of plan's maxAmount at creation
    val planDurationDays: Int,       // Snapshot of plan's durationDays at creation (0 = no time limit)
    var status: DebtStatus,
    val createdByUserId: String,
    val createdAt: Date = Date(),
    var lastUpdatedAt: Date = Date()
)
