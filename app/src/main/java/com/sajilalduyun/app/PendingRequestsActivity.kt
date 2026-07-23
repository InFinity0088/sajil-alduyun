package com.sajilalduyun.app

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.CustomerDebt
import com.sajilalduyun.app.model.DebtHistory
import com.sajilalduyun.app.model.DebtStatus
import com.sajilalduyun.app.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PendingRequestsActivity : BaseActivity() {

    private lateinit var rvPendingRequests: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_requests)

        rvPendingRequests = findViewById(R.id.rvPendingRequests)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnBack = findViewById(R.id.btnBack)

        rvPendingRequests.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { finish() }

        loadPendingRequests()
    }

    private fun loadPendingRequests() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val allPending = db.debtDao().getPendingDebts()

            // Filter by worker if current user is a worker
            val pendingDebts = if (userRole == UserRole.WORKER.name) {
                allPending.filter { it.createdByUserId == userId }
            } else {
                allPending
            }

            val isOwner = userRole == UserRole.OWNER.name

            // Batch-fetch all creator names (fixes N+1 query)
            val creatorIds = pendingDebts.map { it.createdByUserId }.distinct()
            val workerNames = if (creatorIds.isNotEmpty()) {
                val users = db.userDao().getUsersByIds(creatorIds)
                users.associate { it.id to it.name }
            } else {
                emptyMap()
            }

            runOnUiThread {
                if (pendingDebts.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                    rvPendingRequests.visibility = View.GONE
                } else {
                    layoutEmpty.visibility = View.GONE
                    rvPendingRequests.visibility = View.VISIBLE
                    rvPendingRequests.adapter = PendingAdapter(
                        pendingDebts.toMutableList(),
                        workerNames,
                        isOwner,
                        onAction = { debt, action ->
                            handleAction(debt, action)
                        }
                    )
                }
            }
        }
    }

    private fun handleAction(debt: CustomerDebt, action: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)

            if (action == "approve") {
                if (debt.amount == 0.0) {
                    // Payment request — record PAID history, then delete
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
                } else {
                    // Normal approval — record APPROVED history
                    val updatedDebt = debt.copy(status = DebtStatus.APPROVED)
                    val historyEntry = DebtHistory(
                        debtId = debt.id,
                        actionType = "APPROVED",
                        oldAmount = debt.amount,
                        newAmount = debt.amount,
                        changedByUserId = userId
                    )
                    withContext(Dispatchers.IO) {
                        db.debtHistoryDao().insert(historyEntry)
                        db.debtDao().updateDebt(updatedDebt)
                    }
                }
                runOnUiThread {
                    vibrate(50)
                    Toast.makeText(this@PendingRequestsActivity, "تم قبول الطلب", Toast.LENGTH_SHORT).show()
                    removeItemFromList(debt)
                }
            } else if (action == "reject") {
                // Reject — record REJECTED history, then delete
                val historyEntry = DebtHistory(
                    debtId = debt.id,
                    actionType = "REJECTED",
                    oldAmount = debt.amount,
                    newAmount = 0.0,
                    changedByUserId = userId
                )
                withContext(Dispatchers.IO) {
                    db.debtHistoryDao().insert(historyEntry)
                    db.debtDao().deleteDebt(debt)
                }
                runOnUiThread {
                    vibrate(50)
                    Toast.makeText(this@PendingRequestsActivity, "تم رفض الطلب", Toast.LENGTH_SHORT).show()
                    removeItemFromList(debt)
                }
            }
        }
    }

    private fun removeItemFromList(debt: CustomerDebt) {
        val adapter = rvPendingRequests.adapter as? PendingAdapter ?: return
        val position = adapter.removeDebt(debt)
        if (position >= 0) {
            adapter.notifyItemRemoved(position)
        }
        // Check if list is now empty
        if (adapter.itemCount == 0) {
            layoutEmpty.visibility = View.VISIBLE
            rvPendingRequests.visibility = View.GONE
        }
    }

    // Adapter
    inner class PendingAdapter(
        private val debts: MutableList<CustomerDebt>,
        private val workerNames: Map<String, String>,
        private val isOwner: Boolean,
        private val onAction: (CustomerDebt, String) -> Unit
    ) : RecyclerView.Adapter<PendingAdapter.ViewHolder>() {

        fun removeDebt(debt: CustomerDebt): Int {
            val index = debts.indexOfFirst { it.id == debt.id }
            if (index >= 0) {
                debts.removeAt(index)
            }
            return index
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvWorkerName: TextView = view.findViewById(R.id.tvWorkerName)
            val tvCustomerName: TextView = view.findViewById(R.id.tvCustomerName)
            val tvAmount: TextView = view.findViewById(R.id.tvAmount)
            val tvOperationType: TextView = view.findViewById(R.id.tvOperationType)
            val btnApprove: Button = view.findViewById(R.id.btnApprove)
            val btnReject: Button = view.findViewById(R.id.btnReject)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pending_debt, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val debt = debts[position]
            val workerName = workerNames[debt.createdByUserId] ?: "غير معروف"

            holder.tvWorkerName.text = "الكاشير: $workerName"
            holder.tvCustomerName.text = debt.customerName
            holder.tvAmount.text = "${String.format(java.util.Locale.US, "%,.0f", debt.amount)} د.ع"

            // Operation type label
            if (debt.amount == 0.0) {
                holder.tvOperationType.text = "طلب سداد كامل"
                holder.tvOperationType.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            } else {
                holder.tvOperationType.text = "طلب تعديل مبلغ"
                holder.tvOperationType.setTextColor(android.graphics.Color.parseColor("#F57C00"))
            }

            // Only owners can act
            if (isOwner) {
                holder.btnApprove.visibility = View.VISIBLE
                holder.btnReject.visibility = View.VISIBLE
                holder.btnApprove.setOnClickListener { onAction(debt, "approve") }
                holder.btnReject.setOnClickListener { onAction(debt, "reject") }
            } else {
                holder.btnApprove.visibility = View.GONE
                holder.btnReject.visibility = View.GONE
            }
        }

        override fun getItemCount() = debts.size
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
