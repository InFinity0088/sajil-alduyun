package com.sajilalduyun.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.security.SecurityManager
import com.sajilalduyun.app.service.LicenseManager
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {
    override fun isSessionCheckRequired(): Boolean = false

    private lateinit var etUserId: EditText
    private lateinit var etPin: EditText
    private lateinit var btnLogin: com.google.android.material.button.MaterialButton
    private lateinit var tvError: TextView
    private lateinit var tvFingerprintHint: TextView

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_FP_USER_ID = "fingerprint_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isSetupDone = prefs.getBoolean("is_setup_done", false)
        if (!isSetupDone) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        // Check license validity every time app opens
        if (!LicenseManager.isLicenseValid(this)) {
            startActivity(Intent(this, LicenseExpiredActivity::class.java))
            finish()
            return
        }

        // Redirect to account selection (new flow)
        startActivity(Intent(this, AccountSelectionActivity::class.java))
        finish()
    }

    private fun tryFingerprintLogin(prefs: android.content.SharedPreferences) {
        val savedUserId = prefs.getString(KEY_FP_USER_ID, null) ?: return

        val biometricManager = BiometricManager.from(this)

        // Check if biometric is available (any type)
        val canAuthenticate = biometricManager.canAuthenticate()
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) return

        tvFingerprintHint.visibility = View.VISIBLE

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                tvFingerprintHint.visibility = View.GONE
                vibrate(50)
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val user = db.userDao().getUserById(savedUserId)
                    if (user != null && user.isActive) {
                        SecurityManager.recordActivity()
                        val intent = Intent(this@MainActivity, DashboardActivity::class.java)
                        intent.putExtra("USER_ID", user.id)
                        intent.putExtra("USER_ROLE", user.role.name)
                        startActivity(intent)
                        finish()
                    } else {
                        prefs.edit().remove(KEY_FP_USER_ID).apply()
                        tvFingerprintHint.visibility = View.GONE
                        tvError.text = "الحساب غير نشط، سجل دخولك مرة أخرى"
                        tvError.visibility = View.VISIBLE
                    }
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                tvFingerprintHint.visibility = View.GONE
                // Show the error so we can tell if biometric service is refusing
                tvError.text = "بصمة الإصبع غير متاحة ($errorCode)"
                tvError.visibility = View.VISIBLE
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                vibrate(200)
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تسجيل الدخول ببصمة الإصبع")
            .setSubtitle("استخدم بصمتك لتسجيل الدخول السريع")
            .setNegativeButtonText("إلغاء")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun handleLogin() {
        val userId = etUserId.text.toString().trim()
        val pin = etPin.text.toString().trim()

        if (userId.isEmpty() || pin.isEmpty()) {
            showError("يرجى إدخال رمز المستخدم والرقم السري")
            return
        }

        if (SecurityManager.isLockedOut(userId)) {
            val mins = SecurityManager.lockoutMinutesRemaining(userId)
            showError("الحساب مقفل. حاول بعد $mins دقيقة")
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val user = db.userDao().getUserById(userId)

            // If user not found, check if the database has any owner at all
            if (user == null) {
                val owner = db.userDao().getOwner()
                if (owner == null) {
                    runOnUiThread {
                        startActivity(Intent(this@MainActivity, SetupActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    }
                    return@launch
                }
            }

            runOnUiThread {
                if (user == null || !user.isActive) {
                    SecurityManager.recordFailedAttempt(userId)
                    showError("رمز المستخدم غير صحيح")
                    return@runOnUiThread
                }

                if (!SecurityManager.verifyPin(pin, user.pin)) {
                    SecurityManager.recordFailedAttempt(userId)
                    showError("الرقم السري غير صحيح")
                    return@runOnUiThread
                }

                SecurityManager.resetAttempts(userId)
                SecurityManager.recordActivity()

                val intent = Intent(this@MainActivity, DashboardActivity::class.java)
                intent.putExtra("USER_ID", user.id)
                intent.putExtra("USER_ROLE", user.role.name)

                // Navigate to dashboard (the fingerprint enrollment dialog,
                // if shown, will navigate instead so it doesn't get killed)
                navigateToDashboardOrAskFingerprint(intent, userId)
            }
        }
    }

    /**
     * Either navigates to [DashboardActivity] immediately (if the fingerprint
     * enrollment question was already answered), or shows the dialog first and
     * navigates only after the user responds — so the dialog isn't killed mid-air.
     *
     * After the user taps "Yes", the system biometric prompt appears immediately
     * so they can scan their finger and confirm enrollment works.
     */
    private fun navigateToDashboardOrAskFingerprint(intent: Intent, userId: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Already asked before — navigate straight away
        if (prefs.getBoolean("fp_asked_$userId", false)) {
            startActivity(intent)
            finish()
            return
        }

        // Biometric not available — navigate straight away
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate() != BiometricManager.BIOMETRIC_SUCCESS) {
            startActivity(intent)
            finish()
            return
        }

        // Show the enrollment dialog — navigation happens inside the button
        // callbacks, so the dialog stays alive until the user responds.
        // Non-cancelable so the user must explicitly pick Yes or No.
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .setTitle("تفعيل بصمة الإصبع؟")
            .setMessage("لما تسجل الدخول مرة جاية، تقدر تستخدم بصمتك بدل الكتابة.")
            .setPositiveButton("نعم") { _, _ ->
                // Save the preference immediately
                prefs.edit()
                    .putString(KEY_FP_USER_ID, userId)
                    .putBoolean("fp_asked_$userId", true)
                    .apply()
                // Show biometric prompt so the user verifies their fingerprint NOW
                showBiometricPromptAfterEnrollment(intent, userId)
            }
            .setNegativeButton("لا") { _, _ ->
                prefs.edit()
                    .putBoolean("fp_asked_$userId", true)
                    .apply()
                startActivity(intent)
                finish()
            }
            .show()
    }

    /**
     * Shows the system biometric prompt immediately after the user agrees to
     * enable fingerprint login. On success, the scanned fingerprint is unlocked
     * for future use. On cancel/error, we still go to the dashboard (the login
     * has already succeeded via PIN).
     */
    private fun showBiometricPromptAfterEnrollment(intent: Intent, userId: String) {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate() != BiometricManager.BIOMETRIC_SUCCESS) {
            // Biometric no longer available — just go to dashboard
            startActivity(intent)
            finish()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                vibrate(50)
                startActivity(intent)
                finish()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // User cancelled or error — fingerprint is saved for next launch
                startActivity(intent)
                finish()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                vibrate(200)
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تأكيد بصمة الإصبع")
            .setSubtitle("المس المستشعر لتأكيد تفعيل بصمة الإصبع")
            .setNegativeButtonText("تخطي")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}
