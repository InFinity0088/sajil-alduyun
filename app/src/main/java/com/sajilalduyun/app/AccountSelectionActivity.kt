package com.sajilalduyun.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sajilalduyun.app.adapter.AccountCardAdapter
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.User
import com.sajilalduyun.app.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountSelectionActivity : BaseActivity() {
    override fun isSessionCheckRequired(): Boolean = false

    private lateinit var rvAccounts: RecyclerView
    private lateinit var tvNoAccounts: TextView
    private lateinit var tvTitle: TextView
    private lateinit var layoutFingerprintPrompt: LinearLayout

    private var selectedUser: User? = null
    private var accounts: List<User> = emptyList()

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_SELECTED_USER = "selected_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_selection)

        initializeViews()
        loadAccounts()
    }

    private fun initializeViews() {
        rvAccounts = findViewById(R.id.rvAccounts)
        tvNoAccounts = findViewById(R.id.tvNoAccounts)
        tvTitle = findViewById(R.id.tvTitle)
        layoutFingerprintPrompt = findViewById(R.id.layoutFingerprintPrompt)

        rvAccounts.layoutManager = LinearLayoutManager(this)
    }

    private fun loadAccounts() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val allUsers = withContext(Dispatchers.IO) {
                val users = mutableListOf<User>()
                db.userDao().getOwner()?.let { users.add(it) }
                users.addAll(db.userDao().getAllWorkers())
                users
            }

            accounts = allUsers.filter { it.isActive }

            // If no active accounts, show all accounts (fallback)
            if (accounts.isEmpty()) {
                accounts = allUsers
            }

            if (accounts.isEmpty()) {
                rvAccounts.visibility = View.GONE
                tvNoAccounts.visibility = View.VISIBLE
                tvNoAccounts.text = "لا توجد حسابات\nيرجى إعادة تشغيل التطبيق أو التواصل مع الدعم الفني"
                return@launch
            }

            rvAccounts.visibility = View.VISIBLE
            tvNoAccounts.visibility = View.GONE

            val adapter = AccountCardAdapter(accounts) { user ->
                onAccountSelected(user)
            }
            rvAccounts.adapter = adapter
        }
    }

    private fun onAccountSelected(user: User) {
        selectedUser = user
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_USER, user.id).apply()

        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
            promptFingerprint()
        } else {
            promptPassword()
        }
    }

    private fun promptFingerprint() {
        layoutFingerprintPrompt.visibility = View.VISIBLE

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                vibrate(50)
                loginUser(selectedUser!!)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                layoutFingerprintPrompt.visibility = View.GONE
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    promptPassword()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                vibrate(200)
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تسجيل الدخول ببصمة الإصبع")
            .setSubtitle("استخدم بصمتك للوصول إلى ${selectedUser?.name}")
            .setNegativeButtonText("كلمة المرور بدلاً من ذلك")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun promptPassword() {
        val intent = Intent(this, PasswordLoginActivity::class.java)
        intent.putExtra("USER_ID", selectedUser?.id)
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            loginUser(selectedUser!!)
        } else {
            layoutFingerprintPrompt.visibility = View.GONE
            selectedUser = null
        }
    }

    private fun loginUser(user: User) {
        lifecycleScope.launch {
            if (user.isActive) {
                SecurityManager.recordActivity()
                val intent = Intent(this@AccountSelectionActivity, DashboardActivity::class.java)
                intent.putExtra("USER_ID", user.id)
                intent.putExtra("USER_ROLE", user.role.name)
                startActivity(intent)
                finish()
            } else {
                layoutFingerprintPrompt.visibility = View.GONE
                vibrate(100)
            }
        }
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}
