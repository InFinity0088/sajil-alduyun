package com.sajilalduyun.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.sajilalduyun.app.service.LicenseManager

class LicenseExpiredActivity : BaseActivity() {
    override fun isSessionCheckRequired(): Boolean = false

    private lateinit var etRenewalCode: EditText
    private lateinit var tvRenewalError: TextView
    private lateinit var btnRenew: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_expired)

        etRenewalCode = findViewById(R.id.etRenewalCode)
        tvRenewalError = findViewById(R.id.tvRenewalError)
        btnRenew = findViewById(R.id.btnRenew)

        btnRenew.setOnClickListener {
            val code = etRenewalCode.text.toString().trim()

            if (code.isEmpty()) {
                showError("يرجى إدخال رمز التجديد")
                return@setOnClickListener
            }

            val success = LicenseManager.renewLicense(this, code)

            if (success) {
                // Go back to login
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            } else {
                showError("رمز التجديد غير صحيح")
            }
        }
    }

    private fun showError(message: String) {
        tvRenewalError.text = message
        tvRenewalError.visibility = View.VISIBLE
    }
}