package com.sajilalduyun.app

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.PaymentPlan
import com.sajilalduyun.app.ui.MaterialDialogHelper
import com.sajilalduyun.app.util.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlanManagementActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlanAdapter
    private lateinit var emptyView: TextView
    private lateinit var planCountText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plan_management)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        recyclerView  = findViewById(R.id.recyclerPlans)
        emptyView     = findViewById(R.id.tvEmptyPlans)
        planCountText = findViewById(R.id.tvPlanCount)

        adapter = PlanAdapter(
            onToggleActive = { plan -> togglePlanStatus(plan) },
            onDelete       = { plan -> confirmDeletePlan(plan) },
            onEdit         = { plan -> showAddEditPlanDialog(plan) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fabAddPlan
        ).setOnClickListener {
            showAddEditPlanDialog(null)
        }

        loadPlans()
    }

    private fun loadPlans() {
        lifecycleScope.launch {
            val plans = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).planDao().getAllPlans()
            }
            val active   = plans.count { it.isActive }
            val inactive = plans.count { !it.isActive }

            planCountText.text = "($active نشط | $inactive غير نشط)"
            emptyView.visibility    = if (plans.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (plans.isEmpty()) View.GONE   else View.VISIBLE
            adapter.submitList(plans)
        }
    }

    private fun togglePlanStatus(plan: PaymentPlan) {
        lifecycleScope.launch {
            val updated = plan.copy(isActive = !plan.isActive)
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).planDao().update(updated)
            }
            vibrate(50)
            val msg = if (updated.isActive) "تم تفعيل ${plan.name}" else "تم تعطيل ${plan.name}"
            Toast.makeText(this@PlanManagementActivity, msg, Toast.LENGTH_SHORT).show()
            loadPlans()
        }
    }

    private fun confirmDeletePlan(plan: PaymentPlan) {
        MaterialDialogHelper.showDeleteDialog(
            this,
            "الخطة ${plan.name}",
            onConfirm = { deletePlan(plan) }
        )
    }

    private fun deletePlan(plan: PaymentPlan) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).planDao().deleteById(plan.id)
            }
            vibrate(50)
            Toast.makeText(this@PlanManagementActivity, "تم حذف ${plan.name}", Toast.LENGTH_SHORT).show()
            loadPlans()
        }
    }

    private fun showAddEditPlanDialog(existingPlan: PaymentPlan?) {
        val isEditing = existingPlan != null
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_plan, null)

        val etName     = dialogView.findViewById<EditText>(R.id.etPlanName)
        val etDuration = dialogView.findViewById<EditText>(R.id.etPlanDuration)
        val etAmount   = dialogView.findViewById<EditText>(R.id.etPlanAmount)
        val tvError    = dialogView.findViewById<TextView>(R.id.tvPlanDialogError)

        // Pre-fill if editing
        if (isEditing) {
            etName.setText(existingPlan!!.name)
            etDuration.setText(existingPlan!!.durationDays.toString())
            etAmount.setText(NumberFormatter.formatWithCommas(existingPlan!!.maxAmount.toLong()))
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(if (isEditing) "تعديل الخطة" else "إضافة خطة جديدة")
            .setView(dialogView)
            .setPositiveButton(if (isEditing) "حفظ" else "إضافة", null)
            .setNegativeButton("إلغاء", null)
            .show()

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name     = etName.text.toString().trim()
            val durStr   = etDuration.text.toString().trim()
            val amtStr   = NumberFormatter.removeCommas(etAmount.text.toString().trim())

            when {
                name.isEmpty() -> {
                    tvError.visibility = View.VISIBLE; tvError.text = "الرجاء إدخال اسم الخطة"
                }
                durStr.isEmpty() || durStr.toIntOrNull() == null || durStr.toInt() < 0 -> {
                    tvError.visibility = View.VISIBLE; tvError.text = "المدة يجب أن تكون رقم صحيح (0 = بدون مدة)"
                }
                amtStr.isEmpty() || amtStr.toDoubleOrNull() == null || amtStr.toDouble() < 0 -> {
                    tvError.visibility = View.VISIBLE; tvError.text = "المبلغ يجب أن يكون رقم صحيح (0 = بدون حد)"
                }
                else -> {
                    dialog.dismiss()
                    val durationDays = durStr.toInt()
                    val maxAmount   = amtStr.toDouble()
                    savePlan(existingPlan, name, durationDays, maxAmount)
                }
            }
        }
    }

    private fun savePlan(existingPlan: PaymentPlan?, name: String, durationDays: Int, maxAmount: Double) {
        lifecycleScope.launch {
            val plan = if (existingPlan != null) {
                existingPlan.copy(name = name, durationDays = durationDays, maxAmount = maxAmount)
            } else {
                PaymentPlan(name = name, durationDays = durationDays, maxAmount = maxAmount)
            }
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).planDao().insert(plan)
            }
            vibrate(50)
            Toast.makeText(
                this@PlanManagementActivity,
                if (existingPlan != null) "تم حفظ ${plan.name}" else "تم إضافة ${plan.name}",
                Toast.LENGTH_SHORT
            ).show()
            loadPlans()
        }
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// RecyclerView Adapter
// ══════════════════════════════════════════════════════════════════════════════

class PlanAdapter(
    private val onToggleActive: (PaymentPlan) -> Unit,
    private val onDelete: (PaymentPlan) -> Unit,
    private val onEdit: (PaymentPlan) -> Unit
) : RecyclerView.Adapter<PlanAdapter.PlanViewHolder>() {

    private var plans: List<PaymentPlan> = emptyList()

    fun submitList(list: List<PaymentPlan>) {
        plans = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plan, parent, false)
        return PlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(plans[position])
    }

    override fun getItemCount() = plans.size

    inner class PlanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName     = itemView.findViewById<TextView>(R.id.tvPlanName)
        private val tvDetails  = itemView.findViewById<TextView>(R.id.tvPlanDetails)
        private val tvStatus   = itemView.findViewById<TextView>(R.id.tvPlanStatus)
        private val btnToggle  = itemView.findViewById<Button>(R.id.btnTogglePlan)
        private val btnDelete  = itemView.findViewById<TextView>(R.id.btnDeletePlan)

        fun bind(plan: PaymentPlan) {
            tvName.text = plan.name

            val daysLabel = if (plan.durationDays > 0) "${plan.durationDays} يوم" else "بدون مدة"
            val amountLabel = if (plan.maxAmount > 0.0) {
                "سقف ${NumberFormatter.formatWithCommas(plan.maxAmount.toLong())} د.ع"
            } else {
                "بدون سقف"
            }
            tvDetails.text = "$daysLabel | $amountLabel"

            if (plan.isActive) {
                tvStatus.text = "نشط"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#CFFF04"))
                btnToggle.text = "تعطيل"
            } else {
                tvStatus.text = "غير نشط"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#FF4444"))
                btnToggle.text = "تفعيل"
            }

            itemView.setOnClickListener { onEdit(plan) }
            btnToggle.setOnClickListener { onToggleActive(plan) }
            btnDelete.setOnClickListener { onDelete(plan) }
        }
    }
}
