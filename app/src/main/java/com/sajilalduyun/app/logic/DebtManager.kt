package com.sajilalduyun.app.logic

import com.sajilalduyun.app.model.*
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

object DebtManager {

    // --- RULE 1: Check if a debt should be LOCKED based on amount ---
    fun checkAmountLimit(debt: CustomerDebt): DebtStatus {
        // maxLimit == 0.0 means no amount limit
        return if (debt.maxLimit > 0.0 && debt.amount > debt.maxLimit) {
            DebtStatus.LOCKED
        } else {
            DebtStatus.APPROVED
        }
    }

    // --- RULE 2: Check if the debt has exceeded its time limit ---
    // planDurationDays == 0 means no time limit (never overdue)
    // Returns true if the debt is overdue
    fun isOverdue(debt: CustomerDebt): Boolean {
        if (debt.planDurationDays <= 0) return false  // no time limit

        val now = Date()
        val diffInMs = now.time - debt.createdAt.time
        val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMs)

        return diffInDays > debt.planDurationDays
    }

    // --- RULE 3: Full status check combining amount + time ---
    fun evaluateDebtStatus(debt: CustomerDebt): DebtStatus {
        // If overdue, lock regardless of amount
        if (isOverdue(debt)) return DebtStatus.LOCKED

        // Check amount limit
        return checkAmountLimit(debt)
    }

    // --- ROLE CHECK: Can this user perform an action? ---
    fun canUserApprove(user: User): Boolean {
        return user.role == UserRole.OWNER && user.isActive
    }

    fun canUserAddDebt(user: User): Boolean {
        return user.isActive
    }

    fun canUserDeleteDebt(user: User): Boolean {
        return user.role == UserRole.OWNER && user.isActive
    }

    // --- CREATE: Build a new debt with a unique ID ---
    fun createDebt(
        customerName: String,
        amount: Double,
        planId: Long,
        maxLimit: Double,
        planDurationDays: Int,
        createdByUser: User
    ): CustomerDebt {
        val id = "DEBT_${Date().time}_${UUID.randomUUID().toString().take(8)}"

        return CustomerDebt(
            id = id,
            customerName = customerName,
            amount = amount,
            planId = planId,
            maxLimit = maxLimit,
            planDurationDays = planDurationDays,
            status = DebtStatus.PENDING,
            createdByUserId = createdByUser.id
        )
    }
}
