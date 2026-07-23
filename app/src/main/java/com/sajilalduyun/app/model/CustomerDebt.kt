package com.sajilalduyun.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

enum class PlanType {
    THIRTY_DAY,
    UNLIMITED
}

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
    val planType: PlanType,
    val maxLimit: Double,
    var status: DebtStatus,
    val createdByUserId: String,
    val createdAt: Date = Date(),
    var lastUpdatedAt: Date = Date()
)

object DebtFactory {
    fun create(
        id: String,
        customerName: String,
        amount: Double,
        planType: PlanType,
        createdByUserId: String
    ): CustomerDebt {
        val maxLimit = when (planType) {
            PlanType.THIRTY_DAY -> 100_000.0
            PlanType.UNLIMITED  -> 25_000.0
        }
        return CustomerDebt(
            id = id,
            customerName = customerName,
            amount = amount,
            planType = planType,
            maxLimit = maxLimit,
            status = DebtStatus.PENDING,
            createdByUserId = createdByUserId
        )
    }
}