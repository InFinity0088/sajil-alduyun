package com.sajilalduyun.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.content.DialogInterface
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sajilalduyun.app.adapter.AccountCardAdapter
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.User
import com.sajilalduyun.app.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val isOwnerDevice = prefs.getBoolean("is_owner_device", true)

            val usersToShow = withContext(Dispatchers.IO) {
                val users = mutableListOf<User>()
                if (isOwnerDevice) {
                    // Owner device: show owner + all workers
                    db.userDao().getOwner()?.let { users.add(it) }
                    users.addAll(db.userDao().getAllWorkers())
                } else {
                    // Worker device: show only workers, hide owner from list
                    users.addAll(db.userDao().getAllWorkers())
                }
                users
            }

            accounts = usersToShow.filter { it.isActive }

            // If no active accounts, show all (fallback)
            if (accounts.isEmpty()) {
                accounts = usersToShow
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
                // Migrate legacy SHA-256 to bcrypt if needed
                val db = AppDatabase.getDatabase(applicationContext)
                val storedUser = withContext(Dispatchers.IO) {
                    db.userDao().getUserById(user.id)
                }
                if (storedUser != null && !storedUser.pin.startsWith("\$2a\$") && !storedUser.pin.startsWith("\$2b\$")) {
                    // Still SHA-256 — prompt for one-time PIN entry to upgrade
                    val pin = awaitPinMigration(storedUser)
                    if (pin != null && SecurityManager.verifyPin(pin, storedUser.pin)) {
                        val newHash = SecurityManager.rehashPin(pin, storedUser.pin)
                        withContext(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.userDao().updateUser(storedUser.copy(pin = newHash))
                        }
                    }
                }

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

    /**
     * Shows a one-time dialog asking the user to enter their PIN to upgrade
     * the hash from legacy SHA-256 to bcrypt. Returns the PIN if entered,
     * or null if the user dismissed the dialog.
     */
    private suspend fun awaitPinMigration(user: User): String? {
        val result = suspendCancellableCoroutine<String?> { cont ->
            val input = EditText(this@AccountSelectionActivity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                hint = "أدخل الرقم السري"
            }
            MaterialAlertDialogBuilder(this@AccountSelectionActivity)
                .setTitle("ترقية الأمان")
                .setMessage("لترقية أمان حسابك إلى التشفير الحديث، يرجى إدخال رقمك السري مرة واحدة")
                .setView(input)
                .setPositiveButton("تأكيد") { _: DialogInterface, _: Int ->
                    cont.resumeWith(Result.success(input.text.toString()))
                }
                .setNegativeButton("تخطي") { _: DialogInterface, _: Int ->
                    cont.resumeWith(Result.success(null))
                }
                .setOnCancelListener { cont.resumeWith(Result.success(null)) }
                .show()
        }
        return result
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}
