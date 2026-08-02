package com.sajilalduyun.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.CustomerDebt
import com.sajilalduyun.app.model.DebtHistory
import com.sajilalduyun.app.model.DebtStatus
import com.sajilalduyun.app.model.UserRole
import com.sajilalduyun.app.service.SyncService
import com.sajilalduyun.app.ui.MaterialDialogHelper
import com.sajilalduyun.app.util.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DebtDetailActivity : BaseActivity() {

    private lateinit var tvDetailCustomerName: TextView
    private lateinit var tvDetailAmount: TextView
    private lateinit var tvDetailPlan: TextView
    private lateinit var tvDetailStatus: TextView
    private lateinit var tvDetailCreatedAt: TextView
    private lateinit var tvLockedMessage: TextView
    private lateinit var tvLimitLabel: TextView
    private lateinit var progressLimit: ProgressBar
    private lateinit var btnEdit: com.google.android.material.button.MaterialButton
    private lateinit var layoutEditActions: LinearLayout
    private lateinit var etActionAmount: EditText
    private lateinit var layoutEditError: LinearLayout
    private lateinit var tvEditError: TextView
    private lateinit var btnIncrease: com.google.android.material.button.MaterialButton
    private lateinit var btnDecrease: com.google.android.material.button.MaterialButton
    private lateinit var btnMarkPaid: com.google.android.material.button.MaterialButton
    private lateinit var btnDeleteDebt: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var cardHistory: com.google.android.material.card.MaterialCardView
    private lateinit var layoutHistoryContainer: LinearLayout

    private lateinit var debtId: String
    private var currentDebt: CustomerDebt? = null

    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale("ar"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debt_detail)

        debtId = intent.getStringExtra("DEBT_ID") ?: ""

        tvDetailCustomerName = findViewById(R.id.tvDetailCustomerName)
        tvDetailAmount       = findViewById(R.id.tvDetailAmount)
        tvDetailPlan         = findViewById(R.id.tvDetailPlan)
        tvDetailStatus       = findViewById(R.id.tvDetailStatus)
        tvDetailCreatedAt    = findViewById(R.id.tvDetailCreatedAt)
        tvLockedMessage      = findViewById(R.id.tvLockedMessage)
        tvLimitLabel         = findViewById(R.id.tvLimitLabel)
        progressLimit        = findViewById(R.id.progressLimit)
        btnEdit              = findViewById(R.id.btnEdit)
        layoutEditActions    = findViewById(R.id.layoutEditActions)
        etActionAmount       = findViewById(R.id.etActionAmount)
        layoutEditError      = findViewById(R.id.layoutEditError)
        tvEditError          = findViewById(R.id.tvEditError)
        btnIncrease          = findViewById(R.id.btnIncrease)
        btnDecrease          = findViewById(R.id.btnDecrease)
        btnMarkPaid          = findViewById(R.id.btnMarkPaid)
        btnDeleteDebt        = findViewById(R.id.btnDeleteDebt)
        btnBack              = findViewById(R.id.btnBack)
        cardHistory          = findViewById(R.id.cardHistory)
        layoutHistoryContainer = findViewById(R.id.layoutHistoryContainer)

        btnBack.setOnClickListener { finish() }

        btnEdit.setOnClickListener {
            if (layoutEditActions.visibility == View.VISIBLE) {
                // Hide with animation
                layoutEditActions.animate().alpha(0f).translationY(-20f).setDuration(150).withEndAction {
                    layoutEditActions.visibility = View.GONE
                }.start()
                btnEdit.text = "تعديل"
                etActionAmount.text.clear()
                layoutEditError.visibility = View.GONE
            } else {
                // Show with animation
                layoutEditActions.visibility = View.VISIBLE
                layoutEditActions.alpha = 0f
                layoutEditActions.translationY = -20f
                layoutEditActions.animate().alpha(1f).translationY(0f).setDuration(220).start()
                btnEdit.text = "إلغاء التعديل"
                etActionAmount.requestFocus()
            }
        }

        btnIncrease.setOnClickListener { handleAmountChange(increase = true) }
        btnDecrease.setOnClickListener { handleAmountChange(increase = false) }
        btnMarkPaid.setOnClickListener { confirmMarkPaid() }
        btnDeleteDebt.setOnClickListener { confirmDelete() }

        loadDebt()
    }

    private fun loadDebt() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val debt = withContext(Dispatchers.IO) {
                db.debtDao().getDebtById(debtId)
            }

            if (debt == null) {
                runOnUiThread {
                    Toast.makeText(this@DebtDetailActivity, "لم يتم العثور على الدين", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@launch
            }

            currentDebt = debt
            runOnUiThread { bindDebt(debt) }

            // Load history
            val historyEntries = withContext(Dispatchers.IO) {
                db.debtHistoryDao().getHistoryForDebt(debtId)
            }
            runOnUiThread { displayHistory(historyEntries) }
        }
    }

    private fun bindDebt(debt: CustomerDebt) {
        tvDetailCustomerName.text = debt.customerName
        tvDetailAmount.text = "${NumberFormatter.formatWithCommas(debt.amount.toLong())} د.ع"

        val durationLabel = if (debt.planDurationDays > 0) "${debt.planDurationDays} يوم" else "بدون مدة"
        val capLabel = if (debt.maxLimit > 0.0) "${NumberFormatter.formatWithCommas(debt.maxLimit.toLong())} د.ع" else "بدون سقف"
        tvDetailPlan.text = "$durationLabel — حد أقصى $capLabel"

        when (debt.status) {
            DebtStatus.APPROVED -> {
                tvDetailStatus.text = "نشط"
                tvDetailStatus.setTextColor(ContextCompat.getColor(this, R.color.status_active))
                tvDetailStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_active_bg))
            }
            DebtStatus.PENDING -> {
                tvDetailStatus.text = "معلق - بانتظار الموافقة"
                tvDetailStatus.setTextColor(ContextCompat.getColor(this, R.color.status_pending))
                tvDetailStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_pending_bg))
            }
            DebtStatus.LOCKED -> {
                tvDetailStatus.text = "مقفل"
                tvDetailStatus.setTextColor(ContextCompat.getColor(this, R.color.status_locked))
                tvDetailStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_locked_bg))
            }
        }

        // Progress bar
        val percent = if (debt.maxLimit > 0.0) {
            ((debt.amount / debt.maxLimit) * 100).toInt().coerceIn(0, 100)
        } else 0
        progressLimit.progress = percent
        if (percent >= 80) {
            progressLimit.progressTintList = ContextCompat.getColorStateList(this, R.color.status_locked)
        } else {
            progressLimit.progressTintList = ContextCompat.getColorStateList(this, R.color.primary)
        }
        if (debt.maxLimit > 0.0) {
            val remaining = debt.maxLimit - debt.amount
            tvLimitLabel.text = "متبقي: ${NumberFormatter.formatWithCommas(remaining.toLong())} من ${NumberFormatter.formatWithCommas(debt.maxLimit.toLong())} د.ع"
        } else {
            tvLimitLabel.text = "بدون سقف"
        }

        tvDetailCreatedAt.text = dateFormat.format(debt.createdAt)

        // Reset edit state
        layoutEditActions.visibility = View.GONE
        btnEdit.text = "تعديل"
        etActionAmount.text.clear()
        layoutEditError.visibility = View.GONE

        // Role + Status based UI
        if (userRole == UserRole.OWNER.name) {
            // Owner: full access
            tvLockedMessage.visibility = View.GONE
            btnEdit.visibility = View.VISIBLE
            btnDeleteDebt.visibility = View.VISIBLE
        } else {
            // Worker: depends on status
            btnDeleteDebt.visibility = View.GONE
            if (debt.status == DebtStatus.LOCKED) {
                tvLockedMessage.visibility = View.VISIBLE
                btnEdit.visibility = View.GONE
            } else {
                tvLockedMessage.visibility = View.GONE
                btnEdit.visibility = View.VISIBLE
            }
        }
    }

    private fun displayHistory(entries: List<DebtHistory>) {
        if (entries.isEmpty()) {
            cardHistory.visibility = View.GONE
            return
        }
        cardHistory.visibility = View.VISIBLE
        layoutHistoryContainer.removeAllViews()

        // Batch-fetch all changer names (fixes N+1 query)
        val changerIds = entries.map { it.changedByUserId }.distinct()
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val users = if (changerIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    db.userDao().getUsersByIds(changerIds)
                }
            } else emptyList()
            val changerNames = users.associate { it.id to it.name }
            buildHistoryRows(entries, changerNames)
        }
    }

    private fun buildHistoryRows(entries: List<DebtHistory>, changerNames: Map<String, String>) {
        for (entry in entries) {
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_debt_history, layoutHistoryContainer, false)

            val dot = row.findViewById<View>(R.id.viewHistoryDot)
            val tvAction = row.findViewById<TextView>(R.id.tvHistoryAction)
            val tvTime = row.findViewById<TextView>(R.id.tvHistoryTime)
            val tvAmountChange = row.findViewById<TextView>(R.id.tvHistoryAmountChange)
            val tvBy = row.findViewById<TextView>(R.id.tvHistoryBy)

            tvAction.text = getActionLabel(entry.actionType)
            tvTime.text = dateFormat.format(entry.changedAt)
            tvAmountChange.text = "${NumberFormatter.formatWithCommas(entry.oldAmount.toLong())} → ${NumberFormatter.formatWithCommas(entry.newAmount.toLong())} د.ع"

            // Set dot color based on action type
            val dotColor = when (entry.actionType) {
                "CREATED", "APPROVED" -> ContextCompat.getColor(this, R.color.history_created)
                "INCREASED" -> ContextCompat.getColor(this, R.color.history_increased)
                "DECREASED", "PAID" -> ContextCompat.getColor(this, R.color.history_paid)
                "PAYMENT_REQUEST", "REJECTED" -> ContextCompat.getColor(this, R.color.history_rejected)
                "DELETED" -> ContextCompat.getColor(this, R.color.history_deleted)
                else -> ContextCompat.getColor(this, R.color.history_default)
            }
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(dotColor)

            tvBy.text = "بواسطة: ${changerNames[entry.changedByUserId] ?: entry.changedByUserId}"

            if (!entry.notes.isNullOrEmpty()) {
                tvBy.append(" — ${entry.notes}")
            }

            layoutHistoryContainer.addView(row)
        }
    }

    private fun getActionLabel(actionType: String): String {
        return when (actionType) {
            "CREATED" -> "تم إنشاء الدين"
            "INCREASED" -> "زيادة"
            "DECREASED" -> "تخفيض"
            "PAID" -> "تم السداد"
            "PAYMENT_REQUEST" -> "طلب سداد"
            "APPROVED" -> "تمت الموافقة"
            "REJECTED" -> "تم الرفض"
            "DELETED" -> "تم الحذف"
            "MODIFIED" -> "تعديل"
            else -> actionType
        }
    }

    private fun handleAmountChange(increase: Boolean) {
        val amountText = etActionAmount.text.toString().trim()
        val debt = currentDebt ?: return

        if (amountText.isEmpty()) {
            showEditError("يرجى إدخال المبلغ")
            return
        }

        val enteredAmount = amountText.toDoubleOrNull()
        if (enteredAmount == null || enteredAmount <= 0) {
            showEditError("يرجى إدخال مبلغ صحيح")
            return
        }

        val currentAmount = debt.amount
        val newAmount = if (increase) currentAmount + enteredAmount else currentAmount - enteredAmount

        if (!increase && newAmount < 0) {
            vibrate(200)
            showEditError("المبلغ المخفض أكبر من الرصيد")
            return
        }

        if (increase && newAmount > debt.maxLimit) {
            vibrate(200)
            showEditError("تنبيه: راح يتجاوز السقف (${NumberFormatter.formatWithCommas(debt.maxLimit.toLong())} د.ع)")
            return
        }

        // Show confirmation dialog
        val actionLabel = if (increase) "زيادة" else "تخفيض"
        MaterialDialogHelper.showConfirmDialog(
            this,
            "تأكيد $actionLabel",
            "المبلغ الجديد: ${String.format(Locale.US, "%,.0f", newAmount)} د.ع — تأكيد؟",
            "تأكيد",
            "إلغاء",
            onPositive = {
                applyAmountChange(debt.copy(
                    amount = newAmount,
                    status = if (userRole == UserRole.OWNER.name) debt.status else DebtStatus.PENDING,
                    lastUpdatedAt = Date()
                ))
            }
        )
    }

    private fun applyAmountChange(updatedDebt: CustomerDebt) {
        val previousDebt = currentDebt ?: return
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)

            // Record history
            val actionType = if (updatedDebt.amount > previousDebt.amount) "INCREASED" else "DECREASED"
            val historyEntry = DebtHistory(
                debtId = updatedDebt.id,
                actionType = actionType,
                oldAmount = previousDebt.amount,
                newAmount = updatedDebt.amount,
                changedByUserId = userId
            )
            val historyEntries = withContext(Dispatchers.IO) {
                db.debtDao().updateDebt(updatedDebt)
                db.debtHistoryDao().insert(historyEntry)
                db.debtHistoryDao().getHistoryForDebt(debtId)
            }

            // Sync to Firestore
            SyncService.syncDebt(updatedDebt)
            SyncService.syncDebtHistory(historyEntry)

            currentDebt = updatedDebt

            runOnUiThread {
                etActionAmount.text.clear()
                layoutEditError.visibility = View.GONE
                layoutEditActions.visibility = View.GONE
                btnEdit.text = "تعديل"
                bindDebt(updatedDebt)
                displayHistory(historyEntries)
                vibrate(50)
                Toast.makeText(
                    this@DebtDetailActivity,
                    if (userRole == UserRole.OWNER.name) "تم تحديث المبلغ" else "تم إرسال التعديل للمراجعة ✓",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun confirmMarkPaid() {
        val debt = currentDebt ?: return
        MaterialDialogHelper.showConfirmDialog(
            this,
            "تأكيد السداد",
            "تأكيد: راح تسجل هذا الزبون مسدد؟",
            "تأكيد",
            "إلغاء",
            onPositive = { markPaidAndDelete() }
        )
    }

    private fun markPaidAndDelete() {
        val debt = currentDebt ?: return
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)

            if (userRole == UserRole.OWNER.name) {
                // Record history before deleting
                val historyEntry = DebtHistory(
                    debtId = debt.id,
                    actionType = "PAID",
                    oldAmount = debt.amount,
                    newAmount = 0.0,
                    changedByUserId = userId
                )
                withContext(Dispatchers.IO) {
                    db.debtHistoryDao().insert(historyEntry)
                    db.debtDao().deleteDebt(debt)
                }
                // Sync to Firestore
                SyncService.syncDebtHistory(historyEntry)
                SyncService.deleteDebt(debt.id)
                runOnUiThread {
                    vibrate(50)
                    Toast.makeText(this@DebtDetailActivity, "تم تسديد الحساب وحذفه ✓", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                // Worker: set amount=0.0 and status=PENDING for owner approval
                val paymentRequest = debt.copy(
                    amount = 0.0,
                    status = DebtStatus.PENDING,
                    lastUpdatedAt = Date()
                )
                val historyEntry = DebtHistory(
                    debtId = debt.id,
                    actionType = "PAYMENT_REQUEST",
                    oldAmount = debt.amount,
                    newAmount = 0.0,
                    changedByUserId = userId
                )
                withContext(Dispatchers.IO) {
                    db.debtHistoryDao().insert(historyEntry)
                    db.debtDao().updateDebt(paymentRequest)
                }
                // Sync to Firestore
                SyncService.syncDebt(paymentRequest)
                SyncService.syncDebtHistory(historyEntry)
                runOnUiThread {
                    vibrate(50)
                    Toast.makeText(this@DebtDetailActivity, "تم إرسال طلب التسديد للمراجعة ✓", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun confirmDelete() {
        val debt = currentDebt ?: return
        MaterialDialogHelper.showDeleteDialog(
            this,
            "دين ${debt.customerName}",
            onConfirm = { deleteDebt() }
        )
    }

    private fun deleteDebt() {
        val debt = currentDebt ?: return
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val historyEntry = DebtHistory(
                debtId = debt.id,
                actionType = "DELETED",
                oldAmount = debt.amount,
                newAmount = 0.0,
                changedByUserId = userId
            )
            withContext(Dispatchers.IO) {
                db.debtHistoryDao().insert(historyEntry)
                db.debtDao().deleteDebt(debt)
            }
            // Sync to Firestore
            SyncService.syncDebtHistory(historyEntry)
            SyncService.deleteDebt(debt.id)
            runOnUiThread {
                vibrate(50)
                val deletedDebt = debt
                val snackbar = Snackbar.make(
                    findViewById(android.R.id.content),
                    "تم حذف الدين",
                    Snackbar.LENGTH_LONG
                )
                snackbar.view.setBackgroundTintList(
                    ContextCompat.getColorStateList(this@DebtDetailActivity, R.color.snackbar_background)
                )
                snackbar.setActionTextColor(ContextCompat.getColor(this@DebtDetailActivity, R.color.snackbar_action))
                snackbar.setAction("تراجع") { undoDeleteDebt(deletedDebt) }
                snackbar.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)
                        if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                            finish()
                        }
                    }
                })
                snackbar.show()
            }
        }
    }

    private fun undoDeleteDebt(debt: CustomerDebt) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            withContext(Dispatchers.IO) {
                db.debtDao().insertDebt(debt)
            }
            SyncService.syncDebt(debt)
            Toast.makeText(this@DebtDetailActivity, "تم التراجع عن الحذف", Toast.LENGTH_SHORT).show()
            loadDebt()
        }
    }

    private fun showEditError(message: String) {
        tvEditError.text = message
        layoutEditError.visibility = View.VISIBLE
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}
