package com.sajilalduyun.app

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.PasswordResetCode
import com.sajilalduyun.app.security.PasswordResetManager
import com.sajilalduyun.app.security.SecurityManager
import com.sajilalduyun.app.ui.MaterialDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForgotPasswordActivity : BaseActivity() {
    override fun isSessionCheckRequired(): Boolean = false

    private lateinit var btnBack: ImageButton
    private lateinit var tvStepIndicator: TextView
    private lateinit var progressStep: ProgressBar

    private lateinit var layoutStep1: LinearLayout
    private lateinit var layoutStep2: LinearLayout
    private lateinit var layoutStep3: LinearLayout

    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var layoutError: LinearLayout
    private lateinit var tvError: TextView
    private lateinit var btnSendCode: MaterialButton

    private lateinit var etVerificationCode: TextInputEditText
    private lateinit var layoutError2: LinearLayout
    private lateinit var tvError2: TextView
    private lateinit var btnVerifyCode: MaterialButton

    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var layoutError3: LinearLayout
    private lateinit var tvError3: TextView
    private lateinit var btnResetPassword: MaterialButton

    private var currentResetCode: PasswordResetCode? = null
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        tvStepIndicator = findViewById(R.id.tvStepIndicator)
        progressStep = findViewById(R.id.progressStep)

        layoutStep1 = findViewById(R.id.layoutStep1)
        layoutStep2 = findViewById(R.id.layoutStep2)
        layoutStep3 = findViewById(R.id.layoutStep3)

        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        layoutError = findViewById(R.id.layoutError)
        tvError = findViewById(R.id.tvError)
        btnSendCode = findViewById(R.id.btnSendCode)

        etVerificationCode = findViewById(R.id.etVerificationCode)
        layoutError2 = findViewById(R.id.layoutError2)
        tvError2 = findViewById(R.id.tvError2)
        btnVerifyCode = findViewById(R.id.btnVerifyCode)

        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        layoutError3 = findViewById(R.id.layoutError3)
        tvError3 = findViewById(R.id.tvError3)
        btnResetPassword = findViewById(R.id.btnResetPassword)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        btnSendCode.setOnClickListener { handleSendCode() }
        btnVerifyCode.setOnClickListener { handleVerifyCode() }
        btnResetPassword.setOnClickListener { handleResetPassword() }
    }

    private fun handleSendCode() {
        val phone = etPhoneNumber.text.toString().trim()

        if (!isValidIraqiPhone(phone)) {
            showError(layoutError, tvError, "رقم الهاتف يجب أن يكون بالصيغة: 07xxxxxxxxx")
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = withContext(Dispatchers.IO) {
                db.userDao().getUserByPhone(phone)
            }

            if (user == null) {
                runOnUiThread {
                    showError(layoutError, tvError, "لم يتم العثور على حساب بهذا الرقم")
                }
                return@launch
            }

            // Generate and save reset code
            val code = PasswordResetManager.generateResetCode()
            val resetCode = PasswordResetCode(
                userId = user.id,
                phoneNumber = phone,
                code = code
            )

            withContext(Dispatchers.IO) {
                db.passwordResetCodeDao().insert(resetCode)
            }

            currentResetCode = resetCode
            currentUserId = user.id

            // In real app, send code via SMS/WhatsApp/Telegram
            // For now, we show it for demo purposes
            runOnUiThread {
                vibrate(50)
                // Show code to user (in production, this would be sent via SMS/WhatsApp)
                MaterialDialogHelper.showSuccessDialog(
                    this@ForgotPasswordActivity,
                    "تم إرسال الرمز",
                    "الرمز: $code\n\n(في التطبيق الفعلي، سيتم إرساله عبر رسالة نصية)",
                    "حسناً"
                ) {
                    goToStep2()
                }
            }
        }
    }

    private fun handleVerifyCode() {
        val code = etVerificationCode.text.toString().trim()

        if (code.length != 4 || !code.all { it.isDigit() }) {
            showError(layoutError2, tvError2, "الرمز يجب أن يكون 4 أرقام")
            return
        }

        if (currentResetCode?.code != code) {
            showError(layoutError2, tvError2, "الرمز غير صحيح")
            return
        }

        if (PasswordResetManager.isCodeExpired(currentResetCode!!.expiresAt.time)) {
            showError(layoutError2, tvError2, "انتهت صلاحية الرمز (15 دقيقة)")
            return
        }

        vibrate(50)
        goToStep3()
    }

    private fun handleResetPassword() {
        val newPassword = etNewPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        if (newPassword.length < 4) {
            showError(layoutError3, tvError3, "كلمة المرور يجب أن تكون 4 أرقام على الأقل")
            return
        }

        if (newPassword != confirmPassword) {
            showError(layoutError3, tvError3, "كلمات المرور غير متطابقة")
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = withContext(Dispatchers.IO) {
                db.userDao().getUserById(currentUserId!!)
            }

            if (user == null) {
                runOnUiThread { showError(layoutError3, tvError3, "خطأ: المستخدم غير موجود") }
                return@launch
            }

            // Update user password
            val updatedUser = user.copy(
                pin = SecurityManager.hashPin(newPassword)
            )

            // Mark reset code as used
            currentResetCode = currentResetCode!!.copy(isUsed = true)

            withContext(Dispatchers.IO) {
                db.userDao().updateUser(updatedUser)
                db.passwordResetCodeDao().update(currentResetCode!!)
            }

            runOnUiThread {
                vibrate(50)
                MaterialDialogHelper.showSuccessDialog(
                    this@ForgotPasswordActivity,
                    "تم تحديث كلمة المرور",
                    "تم تحديث كلمة المرور بنجاح. يمكنك الآن تسجيل الدخول بكلمة المرور الجديدة.",
                    "حسناً"
                ) {
                    finish()
                }
            }
        }
    }

    private fun goToStep2() {
        layoutStep1.visibility = View.GONE
        layoutStep2.visibility = View.VISIBLE
        layoutStep3.visibility = View.GONE

        tvStepIndicator.text = "الخطوة 2 من 3"
        progressStep.progress = 66
    }

    private fun goToStep3() {
        layoutStep1.visibility = View.GONE
        layoutStep2.visibility = View.GONE
        layoutStep3.visibility = View.VISIBLE

        tvStepIndicator.text = "الخطوة 3 من 3"
        progressStep.progress = 100
    }

    private fun isValidIraqiPhone(phone: String): Boolean {
        return phone.length == 11 && phone.startsWith("07") && phone.all { it.isDigit() }
    }

    private fun showError(layout: LinearLayout, textView: TextView, message: String) {
        textView.text = message
        layout.visibility = View.VISIBLE
        vibrate(100)
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}
