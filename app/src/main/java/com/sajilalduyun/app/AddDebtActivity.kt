package com.sajilalduyun.app

import android.graphics.Color
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.logic.DebtManager
import com.sajilalduyun.app.model.DebtHistory
import com.sajilalduyun.app.model.DebtStatus
import com.sajilalduyun.app.model.PaymentPlan
import com.sajilalduyun.app.model.UserRole
import com.sajilalduyun.app.service.SyncService
import com.sajilalduyun.app.ui.LottieOverlayManager
import com.sajilalduyun.app.util.NumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddDebtActivity : BaseActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var etCustomerName: AutoCompleteTextView
    private lateinit var etAmount: EditText
    private lateinit var layoutPlanCards: LinearLayout
    private lateinit var layoutError: LinearLayout
    private lateinit var tvError: TextView
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton
    private lateinit var lottieManager: LottieOverlayManager

    private var selectedPlanId: Long = -1L
    private var cachedPlans: List<PaymentPlan> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_debt)

        tvTitle = findViewById(R.id.tvTitle)
        etCustomerName = findViewById(R.id.etCustomerName)
        etAmount = findViewById(R.id.etAmount)
        layoutPlanCards = findViewById(R.id.layoutPlanCards)
        layoutError = findViewById(R.id.layoutError)
        tvError = findViewById(R.id.tvError)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        lottieManager = LottieOverlayManager(this)
        lottieManager.initialize(findViewById(android.R.id.content))

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveDebt() }

        // Load plans and populate card dynamically
        lifecycleScope.launch {
            loadPlans()
            loadCustomerNames()
        }
    }

    private suspend fun loadPlans() {
        val db = AppDatabase.getDatabase(applicationContext)
        cachedPlans = withContext(Dispatchers.IO) {
            db.planDao().getActivePlans()
        }

        layoutPlanCards.removeAllViews()

        if (cachedPlans.isEmpty()) return

        val limeStroke = ContextCompat.getColorStateList(this, R.color.primary)!!
        val transparentStroke = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)

        for (plan in cachedPlans) {
            val card = createPlanCard(plan, limeStroke, transparentStroke)
            layoutPlanCards.addView(card)
        }

        // Select first plan by default
        if (cachedPlans.isNotEmpty()) {
            selectPlan(cachedPlans[0].id)
        }
    }

    private fun createPlanCard(
        plan: PaymentPlan,
        limeStroke: android.content.res.ColorStateList,
        transparentStroke: android.content.res.ColorStateList
    ): MaterialCardView {
        val card = MaterialCardView(this)
        val isFirst = cachedPlans.indexOf(plan) == 0

        card.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (isFirst) marginEnd = 8
            else marginStart = 8
        }
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface))
        card.radius = resources.getDimension(com.google.android.material.R.dimen.mtrl_card_corner_radius).toFloat()
        card.cardElevation = 0f
        card.strokeWidth = 0
        card.isClickable = true
        card.isFocusable = true
        card.tag = plan.id
        card.id = View.generateViewId()
        card.setStrokeColor(transparentStroke)

        val inner = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }

        inner.addView(TextView(this).apply {
            text = plan.name
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        val capText = if (plan.maxAmount > 0.0) {
            "سقف ${NumberFormatter.formatWithCommas(plan.maxAmount.toLong())} د.ع"
        } else {
            "بدون سقف"
        }
        inner.addView(TextView(this).apply {
            text = capText
            setTextColor(ContextCompat.getColor(this@AddDebtActivity, R.color.text_secondary))
            textSize = 12f
        })

        if (plan.durationDays > 0) {
            inner.addView(TextView(this).apply {
                text = "ينقفل بعد ${plan.durationDays} يوم"
                setTextColor(ContextCompat.getColor(this@AddDebtActivity, R.color.text_secondary))
                textSize = 11f
            })
        }

        card.addView(inner)

        card.setOnClickListener {
            selectPlan(plan.id)
        }

        return card
    }

    private fun selectPlan(planId: Long) {
        selectedPlanId = planId
        val limeStroke = ContextCompat.getColorStateList(this, R.color.primary)!!
        val transparentStroke = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)

        for (i in 0 until layoutPlanCards.childCount) {
            val child = layoutPlanCards.getChildAt(i)
            if (child is MaterialCardView) {
                val isSelected = child.tag as? Long == planId
                child.setStrokeWidth(if (isSelected) 2 else 0)
                child.setStrokeColor(if (isSelected) limeStroke else transparentStroke)
            }
        }
    }

    private suspend fun loadCustomerNames() {
        val db = AppDatabase.getDatabase(applicationContext)
        try {
            val allDebts = withContext(Dispatchers.IO) {
                db.debtDao().getAllDebts()
            }
            val customerNames = allDebts.map { it.customerName }.distinct().toTypedArray()
            if (customerNames.isNotEmpty()) {
                runOnUiThread {
                    val adapter = ArrayAdapter(
                        this@AddDebtActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        customerNames
                    )
                    etCustomerName.setAdapter(adapter)
                }
            }
        } catch (_: Exception) {
            // Silently skip if DB not available yet
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

        if (selectedPlanId < 0) {
            showError("يرجى اختيار خطة")
            return
        }

        if (userId.isEmpty()) {
            showError("خطأ: المستخدم غير معرّف")
            return
        }

        val selectedPlan = cachedPlans.find { it.id == selectedPlanId }
        if (selectedPlan == null) {
            showError("خطأ: الخطة غير موجودة")
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

                // Create the debt object with plan's snapshot values
                val newDebt = DebtManager.createDebt(
                    customerName = name,
                    amount = amount,
                    planId = selectedPlan.id,
                    maxLimit = selectedPlan.maxAmount,
                    planDurationDays = selectedPlan.durationDays,
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

                // Sync to Firestore
                SyncService.syncDebt(finalDebt)
                SyncService.syncDebtHistory(creationHistory)

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
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}
