package com.sajilalduyun.app.logic

import com.sajilalduyun.app.model.*
import java.util.Date
import java.util.concurrent.TimeUnit

object DebtManager {

    // --- RULE 1: Check if a debt should be LOCKED based on amount ---
    fun checkAmountLimit(debt: CustomerDebt): DebtStatus {
        return when (debt.planType) {
            PlanType.THIRTY_DAY -> {
                if (debt.amount > 100_000.0) DebtStatus.LOCKED
                else DebtStatus.APPROVED
            }
            PlanType.UNLIMITED -> {
                if (debt.amount > 25_000.0) DebtStatus.LOCKED
                else DebtStatus.APPROVED
            }
        }
    }

    // --- RULE 2: Check if 30-day plan has exceeded its time limit ---
    // Returns true if the debt is overdue
    fun isOverdue(debt: CustomerDebt): Boolean {
        if (debt.planType != PlanType.THIRTY_DAY) return false

        val now = Date()
        val diffInMs = now.time - debt.createdAt.time
        val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMs)

        return diffInDays > 30
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
        planType: PlanType,
        createdByUser: User
    ): CustomerDebt {
        // Generate a unique ID using current timestamp
        val id = "DEBT_${Date().time}"

        return DebtFactory.create(
            id = id,
            customerName = customerName,
            amount = amount,
            planType = planType,
            createdByUserId = createdByUser.id
        )
    }
}