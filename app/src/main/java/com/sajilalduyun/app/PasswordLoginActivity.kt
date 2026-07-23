package com.sajilalduyun.app

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasswordLoginActivity : BaseActivity() {
    override fun isSessionCheckRequired(): Boolean = false

    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var etPin: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var layoutError: LinearLayout
    private lateinit var tvError: TextView

    private var selectedUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_login)

        selectedUserId = intent.getStringExtra("USER_ID")

        initializeViews()
        setupListeners()
        loadUserName()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        tvTitle = findViewById(R.id.tvTitle)
        etPin = findViewById(R.id.etPin)
        btnLogin = findViewById(R.id.btnLogin)
        layoutError = findViewById(R.id.layoutError)
        tvError = findViewById(R.id.tvError)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        btnLogin.setOnClickListener { handleLogin() }
    }

    private fun loadUserName() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = withContext(Dispatchers.IO) {
                db.userDao().getUserById(selectedUserId ?: return@withContext null)
            }
            if (user != null) {
                tvTitle.text = "مرحباً، ${user.name}"
            }
        }
    }

    private fun handleLogin() {
        val pin = etPin.text.toString().trim()

        if (pin.isEmpty()) {
            showError("يرجى إدخال الرقم السري")
            return
        }

        if (SecurityManager.isLockedOut(selectedUserId ?: return)) {
            val mins = SecurityManager.lockoutMinutesRemaining(selectedUserId!!)
            showError("الحساب مقفل. حاول بعد $mins دقيقة")
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = withContext(Dispatchers.IO) {
                db.userDao().getUserById(selectedUserId ?: return@withContext null)
            }

            if (user != null && SecurityManager.verifyPin(pin, user.pin)) {
                SecurityManager.resetAttempts(selectedUserId!!)
                vibrate(50)
                setResult(RESULT_OK)
                finish()
            } else {
                SecurityManager.recordFailedAttempt(selectedUserId!!)
                vibrate(200)
                showError("الرقم السري غير صحيح")
                if (SecurityManager.isLockedOut(selectedUserId!!)) {
                    showError("الحساب مقفل بسبب محاولات خاطئة متكررة")
                }
            }
        }
    }

    private fun showError(message: String) {
        tvError.text = message
        layoutError.visibility = View.VISIBLE
        vibrate(100)
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}
