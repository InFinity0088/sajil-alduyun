package com.sajilalduyun.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.User
import com.sajilalduyun.app.model.UserRole
import com.sajilalduyun.app.security.LicenseVerifier
import com.sajilalduyun.app.security.SecurityManager
import kotlinx.coroutines.launch
import java.util.Date
import com.sajilalduyun.app.service.LicenseManager
import com.sajilalduyun.app.service.BackupReminderManager

class SetupActivity : BaseActivity() {
    override fun isSessionCheckRequired(): Boolean = false

    // Choice buttons
    private lateinit var btnChooseOwner: MaterialButton
    private lateinit var btnChooseWorker: MaterialButton

    // Owner form
    private lateinit var cardOwner: MaterialCardView
    private lateinit var etOwnerName: EditText
    private lateinit var etLicenseCode: EditText
    private lateinit var etOwnerPin: EditText
    private lateinit var etOwnerPinConfirm: EditText
    private lateinit var etOwnerPhone: EditText
    private lateinit var tvOwnerError: TextView
    private lateinit var btnCreateOwner: MaterialButton

    // Worker form
    private lateinit var cardWorker: MaterialCardView
    private lateinit var etWorkerName: EditText
    private lateinit var etWorkerCode: EditText
    private lateinit var etWorkerPin: EditText
    private lateinit var etWorkerPhone: EditText
    private lateinit var tvWorkerError: TextView
    private lateinit var btnRegisterWorker: MaterialButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Connect all views
        btnChooseOwner = findViewById(R.id.btnChooseOwner)
        btnChooseWorker = findViewById(R.id.btnChooseWorker)
        cardOwner = findViewById(R.id.cardOwner)
        etOwnerName = findViewById(R.id.etOwnerName)
        etLicenseCode = findViewById(R.id.etLicenseCode)
        etOwnerPin = findViewById(R.id.etOwnerPin)
        etOwnerPinConfirm = findViewById(R.id.etOwnerPinConfirm)
        etOwnerPhone = findViewById(R.id.etOwnerPhone)
        tvOwnerError = findViewById(R.id.tvOwnerError)
        btnCreateOwner = findViewById(R.id.btnCreateOwner)
        cardWorker = findViewById(R.id.cardWorker)
        etWorkerName = findViewById(R.id.etWorkerName)
        etWorkerCode = findViewById(R.id.etWorkerCode)
        etWorkerPin = findViewById(R.id.etWorkerPin)
        etWorkerPhone = findViewById(R.id.etWorkerPhone)
        tvWorkerError = findViewById(R.id.tvWorkerError)
        btnRegisterWorker = findViewById(R.id.btnRegisterWorker)

        // Show owner form when owner is chosen
        btnChooseOwner.setOnClickListener {
            cardOwner.visibility = View.VISIBLE
            cardWorker.visibility = View.GONE
        }

        // Show worker form when worker is chosen
        btnChooseWorker.setOnClickListener {
            cardWorker.visibility = View.VISIBLE
            cardOwner.visibility = View.GONE
        }

        btnCreateOwner.setOnClickListener { createOwnerAccount() }
        btnRegisterWorker.setOnClickListener { registerWorker() }
    }

    private fun createOwnerAccount() {
        val name = etOwnerName.text.toString().trim()
        val licenseCode = etLicenseCode.text.toString().trim()
        val pin = etOwnerPin.text.toString().trim()
        val pinConfirm = etOwnerPinConfirm.text.toString().trim()
        val phone = etOwnerPhone.text.toString().trim()

        if (name.isEmpty()) { showOwnerError("يرجى إدخال اسمك"); return }
        if (licenseCode.isEmpty()) { showOwnerError("يرجى إدخال رمز الترخيص"); return }
        if (!LicenseVerifier.verify(licenseCode, "SAJIL-OWNER-SETUP")) { showOwnerError("رمز الترخيص غير صحيح"); return }
        if (pin.length < 4) { showOwnerError("الرقم السري يجب أن يكون 4 أرقام على الأقل"); return }
        if (pin != pinConfirm) { showOwnerError("الرقم السري غير متطابق"); return }
        if (!isValidIraqiPhone(phone)) { showOwnerError("رقم الهاتف يجب أن يكون بالصيغة: 07xxxxxxxxx"); return }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)

            // Check if owner already exists
            val existingOwner = db.userDao().getOwner()
            if (existingOwner != null) {
                runOnUiThread { showOwnerError("يوجد حساب مالك بالفعل") }
                return@launch
            }

            val owner = User(
                id = "OWNER_001",
                name = name,
                role = UserRole.OWNER,
                pin = SecurityManager.hashPin(pin),
                phoneNumber = phone,
                isActive = true
            )

            db.userDao().insertUser(owner)
            LicenseManager.activateLicense(applicationContext)
            BackupReminderManager.scheduleBackupReminder(applicationContext)

            // Save setup info
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit()
                .putLong("license_start", Date().time)
                .putBoolean("is_setup_done", true)
                .apply()

            runOnUiThread {
                startActivity(Intent(this@SetupActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
        }
    }

    private fun registerWorker() {
        val name = etWorkerName.text.toString().trim()
        val workerCode = etWorkerCode.text.toString().trim()
        val pin = etWorkerPin.text.toString().trim()
        val phone = etWorkerPhone.text.toString().trim()

        if (name.isEmpty()) { showWorkerError("يرجى إدخال اسمك"); return }
        if (workerCode.isEmpty()) { showWorkerError("يرجى إدخال رمز الموظف"); return }
        if (pin.length < 4) { showWorkerError("الرقم السري يجب أن يكون 4 أرقام على الأقل"); return }
        if (!isValidIraqiPhone(phone)) { showWorkerError("رقم الهاتف يجب أن يكون بالصيغة: 07xxxxxxxxx"); return }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)

            // Check if this worker code exists and is not yet activated
            val existingUser = db.userDao().getUserById(workerCode)

            if (existingUser == null) {
                runOnUiThread { showWorkerError("رمز الموظف غير صحيح") }
                return@launch
            }

            if (existingUser.pin.isNotEmpty() && existingUser.pin != "UNSET") {
                runOnUiThread { showWorkerError("هذا الرمز مستخدم بالفعل") }
                return@launch
            }

            // Activate the worker account with their name and PIN
            val activatedWorker = existingUser.copy(
                name = name,
                pin = SecurityManager.hashPin(pin),
                phoneNumber = phone,
                isActive = true
            )

            db.userDao().updateUser(activatedWorker)

            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("is_setup_done", true).apply()

            runOnUiThread {
                startActivity(Intent(this@SetupActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
        }
    }

    private fun showOwnerError(msg: String) {
        tvOwnerError.text = msg
        tvOwnerError.visibility = View.VISIBLE
    }

    private fun showWorkerError(msg: String) {
        tvWorkerError.text = msg
        tvWorkerError.visibility = View.VISIBLE
    }

    private fun isValidIraqiPhone(phone: String): Boolean {
        return when {
            phone.isEmpty() -> false
            phone.length != 11 -> false
            !phone.startsWith("07") -> false
            !phone.all { it.isDigit() } -> false
            else -> true
        }
    }
}