package com.sajilalduyun.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "debt_history")
data class DebtHistory(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val debtId: String,
    val actionType: String,       // "CREATED", "INCREASED", "DECREASED", "PAID", "PAYMENT_REQUEST", "MODIFIED", "DELETED", "APPROVED", "REJECTED"
    val oldAmount: Double,
    val newAmount: Double,
    val changedByUserId: String,
    val notes: String = "",
    val changedAt: Date = Date()
)
