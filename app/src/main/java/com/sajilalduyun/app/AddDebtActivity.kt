package com.sajilalduyun.app

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.logic.DebtManager
import com.sajilalduyun.app.model.DebtHistory
import com.sajilalduyun.app.model.DebtStatus
import com.sajilalduyun.app.model.PlanType
import com.sajilalduyun.app.model.UserRole
import com.sajilalduyun.app.ui.LottieOverlayManager
import com.sajilalduyun.app.util.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddDebtActivity : BaseActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var etCustomerName: AutoCompleteTextView
    private lateinit var etAmount: EditText
    private lateinit var cardPlanThirty: MaterialCardView
    private lateinit var cardPlanUnlimited: MaterialCardView
    private lateinit var layoutError: LinearLayout
    private lateinit var tvError: TextView
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton
    private lateinit var layoutPlanSection: LinearLayout
    private lateinit var lottieManager: LottieOverlayManager

    private var selectedPlan: PlanType = PlanType.THIRTY_DAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_debt)

        tvTitle = findViewById(R.id.tvTitle)
        etCustomerName = findViewById(R.id.etCustomerName)
        etAmount = findViewById(R.id.etAmount)
        cardPlanThirty = findViewById(R.id.cardPlanThirty)
        cardPlanUnlimited = findViewById(R.id.cardPlanUnlimited)
        layoutError = findViewById(R.id.layoutError)
        tvError = findViewById(R.id.tvError)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)
        layoutPlanSection = findViewById(R.id.layoutPlanSection)

        lottieManager = LottieOverlayManager(this)
        lottieManager.initialize(findViewById(android.R.id.content))

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveDebt() }

        // Plan card selection
        val limeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#CFFF04"))
        cardPlanThirty.setOnClickListener {
            selectedPlan = PlanType.THIRTY_DAY
            cardPlanThirty.setStrokeWidth(2)
            cardPlanThirty.setStrokeColor(limeColor)
            cardPlanUnlimited.setStrokeWidth(0)
        }

        cardPlanUnlimited.setOnClickListener {
            selectedPlan = PlanType.UNLIMITED
            cardPlanUnlimited.setStrokeWidth(2)
            cardPlanUnlimited.setStrokeColor(limeColor)
            cardPlanThirty.setStrokeWidth(0)
        }

        // Auto-format amount input with commas as user types
        etAmount.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isEditing) return
                isEditing = true
                val raw = s?.toString()?.filter { it.isDigit() } ?: ""
                if (raw.isNotEmpty()) {
                    val formatted = NumberFormatter.formatWithCommas(raw.toLong())
                    s?.replace(0, s.length, formatted)
                }
                isEditing = false
            }
        })

        // Load customer names for autocomplete
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val allDebts = db.debtDao().getAllDebts()
            val customerNames = allDebts.map { it.customerName }.distinct().toTypedArray()
            if (customerNames.isNotEmpty()) {
                val adapter = ArrayAdapter(this@AddDebtActivity, android.R.layout.simple_dropdown_item_1line, customerNames)
                etCustomerName.setAdapter(adapter)
            }
        }
    }

    private fun saveDebt() {
        val name = etCustomerName.text.toString().trim()

        if (name.isEmpty()) {
            showError("يرجى إدخال اسم الزبون")
            return
        }

        val amountText = NumberFormatter.removeCommas(etAmount.text.toString().trim())

        if (amountText.isEmpty()) {
            showError("يرجى إدخال المبلغ")
            return
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            showError("يرجى إدخال مبلغ صحيح")
            return
        }

        if (userId.isEmpty()) {
            showError("خطأ: المستخدم غير معرّف")
            return
        }

        lottieManager.showLoading()

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val currentUser = withContext(Dispatchers.IO) {
                    db.userDao().getUserById(userId)
                }

                if (currentUser == null) {
                    runOnUiThread {
                        lottieManager.hideLoading()
                        showError("خطأ: المستخدم غير موجود")
                    }
                    return@launch
                }

                // Create the debt object
                val newDebt = DebtManager.createDebt(
                    customerName = name,
                    amount = amount,
                    planType = selectedPlan,
                    createdByUser = currentUser
                )

                // Workers create PENDING debts, Owners create APPROVED debts directly
                val finalDebt = if (currentUser.role == UserRole.OWNER) {
                    newDebt.copy(status = DebtStatus.APPROVED)
                } else {
                    newDebt // stays PENDING
                }

                // Record creation history
                val creationHistory = DebtHistory(
                    debtId = finalDebt.id,
                    actionType = "CREATED",
                    oldAmount = 0.0,
                    newAmount = finalDebt.amount,
                    changedByUserId = userId
                )
                withContext(Dispatchers.IO) {
                    db.debtDao().insertDebt(finalDebt)
                    db.debtHistoryDao().insert(creationHistory)
                }

                runOnUiThread {
                    lottieManager.hideLoading()
                    vibrate(50)
                    lottieManager.showSuccess(1500)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 1500)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    lottieManager.hideLoading()
                    showError("حدث خطأ: ${e.message}")
                }
            }
        }
    }

    private fun showError(message: String) {
        tvError.text = message
        layoutError.visibility = View.VISIBLE
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
