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
import com.sajilalduyun.app.model.User
import com.sajilalduyun.app.model.UserRole
import com.sajilalduyun.app.security.SecurityManager
import com.sajilalduyun.app.ui.MaterialDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class WorkerManagementActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkerAdapter
    private lateinit var emptyView: TextView
    private lateinit var workerCountText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_workers)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        recyclerView    = findViewById(R.id.recyclerWorkers)
        emptyView       = findViewById(R.id.tvEmptyWorkers)
        workerCountText = findViewById(R.id.tvWorkerCount)

        adapter = WorkerAdapter(
            onToggleActive = { worker -> toggleWorkerStatus(worker) },
            onDelete       = { worker -> confirmDeleteWorker(worker) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fabAddWorker
        ).setOnClickListener {
            showAddWorkerDialog()
        }

        loadWorkers()
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private fun loadWorkers() {
        lifecycleScope.launch {
            val workers = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).userDao().getAllWorkers()
            }
            val active   = workers.count { it.isActive }
            val inactive = workers.count { !it.isActive }

            workerCountText.text = "العمال: $active نشط  |  $inactive غير نشط"
            emptyView.visibility    = if (workers.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (workers.isEmpty()) View.GONE   else View.VISIBLE
            adapter.submitList(workers)
        }
    }

    private fun toggleWorkerStatus(worker: User) {
        lifecycleScope.launch {
            val updated = worker.copy(isActive = !worker.isActive)
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).userDao().updateUser(updated)
            }
            vibrate(50)
            val msg = if (updated.isActive) "تم تفعيل ${worker.name}" else "تم تعطيل ${worker.name}"
            Toast.makeText(this@WorkerManagementActivity, msg, Toast.LENGTH_SHORT).show()
            loadWorkers()
        }
    }

    private fun confirmDeleteWorker(worker: User) {
        MaterialDialogHelper.showDeleteDialog(
            this,
            "العامل ${worker.name}",
            onConfirm = { deleteWorker(worker) }
        )
    }

    private fun deleteWorker(worker: User) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).userDao().deleteUser(worker)
            }
            vibrate(50)
            Toast.makeText(this@WorkerManagementActivity, "تم حذف ${worker.name}", Toast.LENGTH_SHORT).show()
            loadWorkers()
        }
    }

    // ── Add Worker Dialog ─────────────────────────────────────────────────────

    private fun showAddWorkerDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_worker, null)

        val etName       = dialogView.findViewById<EditText>(R.id.etWorkerName)
        val etPin        = dialogView.findViewById<EditText>(R.id.etWorkerPin)
        val etPinConfirm = dialogView.findViewById<EditText>(R.id.etWorkerPinConfirm)
        val tvError      = dialogView.findViewById<TextView>(R.id.tvWorkerDialogError)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("إضافة عامل جديد")
            .setView(dialogView)
            .setPositiveButton("إضافة", null)
            .setNegativeButton("إلغاء", null)
            .show()

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name       = etName.text.toString().trim()
            val pin        = etPin.text.toString().trim()
            val pinConfirm = etPinConfirm.text.toString().trim()

            when {
                name.isEmpty() -> {
                    tvError.visibility = View.VISIBLE
                    tvError.text = "الرجاء إدخال اسم العامل"
                }
                pin.length != 4 || !pin.all { it.isDigit() } -> {
                    tvError.visibility = View.VISIBLE
                    tvError.text = "الرمز السري يجب أن يكون 4 أرقام"
                }
                pin != pinConfirm -> {
                    tvError.visibility = View.VISIBLE
                    tvError.text = "الرمزان السريان غير متطابقان"
                }
                else -> {
                    dialog.dismiss()
                    createWorker(name, pin)
                }
            }
        }
    }

    private fun createWorker(name: String, pin: String) {
        lifecycleScope.launch {
            val hashedPin = SecurityManager.hashPin(pin)
            // Generate a short, unique worker code: W-XXXX (e.g. W-3821)
            val workerCode = "W-${(1000..9999).random()}"
            val newWorker = User(
                id       = workerCode,
                name     = name,
                role     = UserRole.WORKER,
                pin      = hashedPin,
                isActive = true
            )
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).userDao().insertUser(newWorker)
            }
            vibrate(50)

            // Show a professional dialog with the worker code — not a quick Toast
            MaterialDialogHelper.showSuccessDialog(
                this@WorkerManagementActivity,
                "تم إضافة العامل",
                """
                    الاسم: $name

                    رمز الدخول: $workerCode

                    سلم هذا الرمز للعامل لتسجيل الدخول
                """.trimIndent()
            )

            loadWorkers()
        }
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// RecyclerView Adapter
// ══════════════════════════════════════════════════════════════════════════════

class WorkerAdapter(
    private val onToggleActive: (User) -> Unit,
    private val onDelete: (User) -> Unit
) : RecyclerView.Adapter<WorkerAdapter.WorkerViewHolder>() {

    private var workers: List<User> = emptyList()

    fun submitList(list: List<User>) {
        workers = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_worker, parent, false)
        return WorkerViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkerViewHolder, position: Int) {
        holder.bind(workers[position])
    }

    override fun getItemCount() = workers.size

    inner class WorkerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName          = itemView.findViewById<TextView>(R.id.tvWorkerName)
        private val tvStatus        = itemView.findViewById<TextView>(R.id.tvWorkerStatus)
        private val tvWorkerCode    = itemView.findViewById<TextView>(R.id.tvWorkerCode)
        private val btnToggle       = itemView.findViewById<Button>(R.id.btnToggleWorker)
        private val btnDelete       = itemView.findViewById<TextView>(R.id.btnDeleteWorker)
        private val btnCopyCode     = itemView.findViewById<ImageButton>(R.id.btnCopyWorkerCode)
        private val statusIndicator = itemView.findViewById<View>(R.id.viewStatusIndicator)

        fun bind(worker: User) {
            tvName.text = worker.name
            tvWorkerCode.text = worker.id

            if (worker.isActive) {
                tvStatus.text = "نشط"
                tvStatus.setTextColor(itemView.context.getColor(R.color.status_active))
                statusIndicator.setBackgroundColor(itemView.context.getColor(R.color.status_active))
                btnToggle.text = "تعطيل"
            } else {
                tvStatus.text = "غير نشط"
                tvStatus.setTextColor(itemView.context.getColor(R.color.status_locked))
                statusIndicator.setBackgroundColor(itemView.context.getColor(R.color.status_locked))
                btnToggle.text = "تفعيل"
            }

            btnCopyCode.setOnClickListener {
                val clipboard = itemView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Worker Code", worker.id)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "تم نسخ الرمز: ${worker.id}", Toast.LENGTH_SHORT).show()
            }

            btnToggle.setOnClickListener { onToggleActive(worker) }
            btnDelete.setOnClickListener { onDelete(worker) }
        }
    }
}