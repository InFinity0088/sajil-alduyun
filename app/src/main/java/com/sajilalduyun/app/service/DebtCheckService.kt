package com.sajilalduyun.app.service

import android.content.Context
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.logic.DebtManager
import com.sajilalduyun.app.model.DebtStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DebtCheckService {

    // Call this every time the app opens or dashboard loads.
    // Accepts a CoroutineScope from the caller (e.g. lifecycleScope) for lifecycle-aware execution.
    fun checkAllDebts(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val allDebts = db.debtDao().getAllDebts()

            var alertId = 1000 // Starting notification ID

            allDebts.forEach { debt ->
                // Skip already locked debts
                if (debt.status == DebtStatus.LOCKED) return@forEach

                // Check if debt exceeded its time limit (planDurationDays > 0)
                if (debt.planDurationDays > 0 && DebtManager.isOverdue(debt)) {
                    // Update status to LOCKED in database
                    val updatedDebt = debt.copy(status = DebtStatus.LOCKED)
                    db.debtDao().updateDebt(updatedDebt)

                    // Send notification
                    NotificationHelper.sendAlert(
                        context,
                        "تجاوز المدة المحددة",
                        "الزبون ${debt.customerName} تجاوز ${debt.planDurationDays} يوماً بدون سداد",
                        alertId++
                    )
                }

                // Check if amount exceeded limit (maxLimit > 0)
                if (debt.maxLimit > 0.0 && debt.amount > debt.maxLimit) {
                    val updatedDebt = debt.copy(status = DebtStatus.LOCKED)
                    db.debtDao().updateDebt(updatedDebt)

                    NotificationHelper.sendAlert(
                        context,
                        "تجاوز الحد المسموح",
                        "الزبون ${debt.customerName} تجاوز الحد المسموح به",
                        alertId++
                    )
                }
            }
        }
    }
}
