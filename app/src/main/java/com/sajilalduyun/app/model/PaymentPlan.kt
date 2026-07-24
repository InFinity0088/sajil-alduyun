package com.sajilalduyun.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A customizable payment plan that defines the rules for a debt.
 *
 * The owner can create, edit, and delete these from within the app.
 * Each debt stores a snapshot of the plan's values at creation time
 * (maxLimit, planDurationDays) so changing a plan doesn't retroactively
 * affect existing debts.
 *
 * @property id Auto-generated primary key.
 * @property name User-visible name, e.g. "30 يوم", "أسبوعي", "مفتوح".
 * @property durationDays Payment period in days. 0 = no time limit.
 * @property maxAmount Maximum debt amount allowed. 0.0 = no amount limit.
 * @property isActive Soft-delete: inactive plans are hidden from selection.
 */
@Entity(tableName = "plans")
data class PaymentPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val durationDays: Int = 0,
    val maxAmount: Double = 0.0,
    val isActive: Boolean = true
)
