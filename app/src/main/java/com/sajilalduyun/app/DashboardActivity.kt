package com.sajilalduyun.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.CustomerDebt
import com.sajilalduyun.app.model.DebtStatus
import com.sajilalduyun.app.model.UserRole
import com.sajilalduyun.app.service.BackupService
import com.sajilalduyun.app.service.DebtCheckService
import com.sajilalduyun.app.service.NotificationHelper
import com.sajilalduyun.app.util.NumberFormatter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.DefaultItemAnimator
import kotlinx.coroutines.launch

class DashboardActivity : BaseActivity() {

    // Owner views
    private lateinit var layoutOwner: LinearLayout
    private lateinit var tvWelcome: TextView
    private lateinit var tvOwnerName: TextView
    private lateinit var tvActiveCount: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnLogoutOwner: ImageButton
    private lateinit var btnManageWorkers: ImageButton
    private lateinit var btnManagePlans: ImageButton
    private lateinit var btnNotifications: ImageButton
    private lateinit var rvDebts: RecyclerView
    private lateinit var horizontalScrollAlerts: HorizontalScrollView
    private lateinit var layoutAlerts: LinearLayout
    private lateinit var btnAddDebt: FloatingActionButton
    private lateinit var tvPendingPill: TextView
    private lateinit var btnImport: ImageButton

    // Worker views
    private lateinit var layoutWorker: LinearLayout
    private lateinit var tvWelcomeWorker: TextView
    private lateinit var tvWorkerName: TextView
    private lateinit var etWorkerSearch: EditText
    private lateinit var btnWorkerAddDebt: com.google.android.material.button.MaterialButton
    private lateinit var rvWorkerDebts: RecyclerView
    private lateinit var tvWorkerPendingLink: TextView
    private lateinit var btnLogoutWorker: ImageButton

    // Filterable lists
    private var allOwnerDebts: List<CustomerDebt> = emptyList()
    private var allWorkerDebts: List<CustomerDebt> = emptyList()

    // Persistent adapter instances (reused instead of recreating on every keystroke/resume)
    private var debtAdapter: GeneralDebtAdapter? = null
    private var workerDebtAdapter: GeneralDebtAdapter? = null

    // File picker for importing JSON backup
    private val importBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val jsonContent = BackupService.readUriContent(this@DashboardActivity, uri)
            if (jsonContent == null) {
                Toast.makeText(this@DashboardActivity, "فشل قراءة الملف", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val result = BackupService.importJson(this@DashboardActivity, jsonContent)
            // Show result on main thread
            runOnUiThread {
                if (result.errors.isEmpty()) {
                    Toast.makeText(
                        this@DashboardActivity,
                        "تم الاستيراد: ${result.debtsImported} دين، ${result.usersImported} مستخدم، ${result.historyImported} سجل",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val errorSummary = result.errors.take(3).joinToString("\n")
                    MaterialAlertDialogBuilder(this@DashboardActivity)
                        .setTitle("نتيجة الاستيراد")
                        .setMessage(
                            "تم استيراد:\n" +
                            "• ${result.debtsImported} دين\n" +
                            "• ${result.usersImported} مستخدم\n" +
                            "• ${result.historyImported} سجل\n\n" +
                            if (result.errors.isNotEmpty()) "الأخطاء:\n$errorSummary" else ""
                        )
                        .setPositiveButton("حسناً", null)
                        .show()
                }
                // Refresh dashboard
                loadDashboard()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Owner views
        layoutOwner = findViewById(R.id.layoutOwner)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvOwnerName = findViewById(R.id.tvOwnerName)
        tvActiveCount = findViewById(R.id.tvActiveCount)
        tvPendingCount = findViewById(R.id.tvPendingCount)
        etSearch = findViewById(R.id.etSearch)
        btnLogoutOwner = findViewById(R.id.btnLogoutOwner)
        btnManageWorkers = findViewById(R.id.btnManageWorkers)
        btnManagePlans = findViewById(R.id.btnManagePlans)
        btnNotifications = findViewById(R.id.btnNotifications)
        rvDebts = findViewById(R.id.rvDebts)
        horizontalScrollAlerts = findViewById(R.id.horizontalScrollAlerts)
        layoutAlerts = findViewById(R.id.layoutAlerts)
        btnAddDebt = findViewById(R.id.btnAddDebt)
        tvPendingPill = findViewById(R.id.tvPendingPill)
        btnImport = findViewById(R.id.btnImport)

        // Worker views
        layoutWorker = findViewById(R.id.layoutWorker)
        tvWelcomeWorker = findViewById(R.id.tvWelcomeWorker)
        tvWorkerName = findViewById(R.id.tvWorkerName)
        etWorkerSearch = findViewById(R.id.etWorkerSearch)
        btnWorkerAddDebt = findViewById(R.id.btnWorkerAddDebt)
        rvWorkerDebts = findViewById(R.id.rvWorkerSearchResults)
        tvWorkerPendingLink = findViewById(R.id.tvWorkerPendingLink)
        btnLogoutWorker = findViewById(R.id.btnLogoutWorker)

        rvDebts.layoutManager = LinearLayoutManager(this)
        rvDebts.itemAnimator = DefaultItemAnimator()
        rvWorkerDebts.layoutManager = LinearLayoutManager(this)

        // Owner search TextWatcher
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                val filtered = if (query.isEmpty()) allOwnerDebts
                else allOwnerDebts.filter { it.customerName.contains(query, ignoreCase = true) }
                debtAdapter?.updateData(filtered)
            }
        })

        // Worker search TextWatcher
        etWorkerSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                val filtered = if (query.isEmpty()) allWorkerDebts
                else allWorkerDebts.filter { it.customerName.contains(query, ignoreCase = true) }
                workerDebtAdapter?.updateData(filtered)
            }
        })

        NotificationHelper.createNotificationChannel(this)
        DebtCheckService.checkAllDebts(this, lifecycleScope)

        // ── Role-based layout ──────────────────────────────────────────────────
        if (userRole == UserRole.WORKER.name) {
            layoutOwner.visibility = View.GONE
            layoutWorker.visibility = View.VISIBLE

            btnWorkerAddDebt.setOnClickListener {
                val intent = Intent(this, AddDebtActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("USER_ROLE", userRole)
                startActivity(intent)
            }

            tvWorkerPendingLink.setOnClickListener {
                val intent = Intent(this, PendingRequestsActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("USER_ROLE", userRole)
                startActivity(intent)
            }

            btnLogoutWorker.setOnClickListener { logout() }

        } else {
            // Owner
            layoutWorker.visibility = View.GONE
            layoutOwner.visibility = View.VISIBLE

            btnManageWorkers.visibility = View.VISIBLE
            btnManagePlans.visibility = View.VISIBLE
            btnAddDebt.visibility = View.VISIBLE

            btnAddDebt.setOnClickListener {
                val intent = Intent(this, AddDebtActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("USER_ROLE", userRole)
                startActivity(intent)
            }

            btnManageWorkers.setOnClickListener {
                val intent = Intent(this, WorkerManagementActivity::class.java)
                intent.putExtra("USER_ID", userId)
                startActivity(intent)
            }

            btnManagePlans.setOnClickListener {
                val intent = Intent(this, PlanManagementActivity::class.java)
                intent.putExtra("USER_ID", userId)
                startActivity(intent)
            }

            btnLogoutOwner.setOnClickListener { logout() }

            btnImport.visibility = View.VISIBLE
            btnImport.setOnClickListener {
                importBackupLauncher.launch(arrayOf("application/json", "*/*"))
            }
            btnImport.setOnLongClickListener {
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val allDebts = db.debtDao().getAllDebts()
                    val allUsers = db.userDao().getAllWorkers() + listOfNotNull(db.userDao().getOwner())
                    val allHistory = allDebts.flatMap {
                        db.debtHistoryDao().getHistoryForDebt(it.id)
                    }
                    runOnUiThread {
                        BackupService.exportJson(this@DashboardActivity, allDebts, allUsers, allHistory)
                    }
                }
                true
            }

            tvPendingPill.setOnClickListener {
                val intent = Intent(this, PendingRequestsActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("USER_ROLE", userRole)
                startActivity(intent)
            }
        }
        // Data loaded in onResume (fires after onCreate and on return from other activities)
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = db.userDao().getUserById(userId)
            val allDebts = db.debtDao().getAllDebts()
            val pendingDebts = db.debtDao().getPendingDebts()

            runOnUiThread {
                if (userRole == UserRole.OWNER.name) {
                    tvWelcome.text = "مرحباً"
                    tvOwnerName.text = user?.name ?: ""

                    allOwnerDebts = allDebts

                    // Active accounts count (APPROVED)
                    val activeCount = allDebts.count { it.status == DebtStatus.APPROVED }
                    tvActiveCount.text = activeCount.toString()

                    // Pending count
                    tvPendingCount.text = pendingDebts.size.toString()

                    // Debt list — create once, update on subsequent calls
                    if (debtAdapter == null) {
                        debtAdapter = GeneralDebtAdapter(allOwnerDebts)
                        rvDebts.adapter = debtAdapter
                    } else {
                        debtAdapter!!.updateData(allOwnerDebts)
                    }

                    // Urgent alerts strip for LOCKED debts
                    val lockedDebts = allDebts.filter { it.status == DebtStatus.LOCKED }
                    if (lockedDebts.isEmpty()) {
                        horizontalScrollAlerts.visibility = View.GONE
                    } else {
                        horizontalScrollAlerts.visibility = View.VISIBLE
                        layoutAlerts.removeAllViews()
                        for (ld in lockedDebts) {
                            val alertCard = createAlertCard(ld)
                            layoutAlerts.addView(alertCard)
                        }
                    }

                    // Pending pill at bottom
                    updatePendingPill(pendingDebts.size)

                } else {
                    // Worker
                    tvWelcomeWorker.text = "مرحباً"
                    tvWorkerName.text = user?.name ?: ""
                    allWorkerDebts = allDebts
                    if (workerDebtAdapter == null) {
                        workerDebtAdapter = GeneralDebtAdapter(allWorkerDebts)
                        rvWorkerDebts.adapter = workerDebtAdapter
                    } else {
                        workerDebtAdapter!!.updateData(allWorkerDebts)
                    }
                }
            }
        }
    }

    private fun createAlertCard(debt: CustomerDebt): View {
        val card = androidx.cardview.widget.CardView(this)
        val params = LinearLayout.LayoutParams(
            200, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 12, 0)
        card.layoutParams = params
        card.setCardBackgroundColor(Color.parseColor("#1C1400"))
        card.radius = 12f
        card.cardElevation = 0f

        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.setPadding(14, 8, 14, 8)
        inner.gravity = android.view.Gravity.CENTER

        val nameTv = TextView(this)
        nameTv.text = debt.customerName
        nameTv.setTextColor(Color.WHITE)
        nameTv.textSize = 13f
        nameTv.setTypeface(null, android.graphics.Typeface.BOLD)

        val reasonTv = TextView(this)
        reasonTv.setTextColor(Color.parseColor("#FF8C00"))
        reasonTv.textSize = 11f
        reasonTv.text = if (debt.planDurationDays > 0) "متأخر +${debt.planDurationDays} يوم" else "تجاوز السقف"

        inner.addView(nameTv)
        inner.addView(reasonTv)
        card.addView(inner)
        return card
    }

    private fun updatePendingPill(count: Int) {
        if (count == 0) {
            tvPendingPill.visibility = View.GONE
        } else {
            tvPendingPill.visibility = View.VISIBLE
            tvPendingPill.text = "$count طلبات معلقة — اضغط للمراجعة"
        }
    }

    // ── Unified Debt Adapter (replaces both DebtAdapter and WorkerDebtAdapter) ──

    inner class GeneralDebtAdapter(
        initialDebts: List<CustomerDebt>
    ) : RecyclerView.Adapter<GeneralDebtAdapter.ViewHolder>() {

        private val debts = initialDebts.toMutableList()

        fun updateData(newDebts: List<CustomerDebt>) {
            debts.clear()
            debts.addAll(newDebts)
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName:   TextView = view.findViewById(R.id.tvDebtCustomerName)
            val tvStatus: TextView = view.findViewById(R.id.tvDebtStatus)
            val tvAmount: TextView = view.findViewById(R.id.tvDebtAmount)
            val tvPlan:   TextView = view.findViewById(R.id.tvDebtPlan)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_debt, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val debt = debts[position]
            holder.tvName.text   = debt.customerName
            holder.tvAmount.text = "${NumberFormatter.formatWithCommas(debt.amount.toLong())} د.ع"
            holder.tvPlan.text   = if (debt.planDurationDays > 0) "${debt.planDurationDays} يوم" else "مفتوح"

            when (debt.status) {
                DebtStatus.APPROVED -> {
                    holder.tvStatus.text = "نشط"
                    holder.tvStatus.setTextColor(Color.parseColor("#CFFF04"))
                }
                DebtStatus.PENDING -> {
                    holder.tvStatus.text = "معلق"
                    holder.tvStatus.setTextColor(Color.parseColor("#FF8C00"))
                }
                DebtStatus.LOCKED -> {
                    holder.tvStatus.text = "مقفل"
                    holder.tvStatus.setTextColor(Color.parseColor("#FF4444"))
                }
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(holder.itemView.context, DebtDetailActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("USER_ROLE", userRole)
                intent.putExtra("DEBT_ID", debt.id)
                startActivity(intent)
            }
        }

        override fun getItemCount() = debts.size
    }

    private fun logout() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
