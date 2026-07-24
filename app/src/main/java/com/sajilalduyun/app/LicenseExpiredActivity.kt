package com.sajilalduyun.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.sajilalduyun.app.service.LicenseManager

class LicenseExpiredActivity : BaseActivity() {
    override fun isSessionCheckRequired(): Boolean = false

    private lateinit var etRenewalCode: EditText
    private lateinit var tvRenewalError: TextView
    private lateinit var btnRenew: MaterialButton

    // Revoke views
    private lateinit var tvToggleRevoke: TextView
    private lateinit var layoutRevoke: LinearLayout
    private lateinit var etRevokeCode: EditText
    private lateinit var btnRevoke: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_expired)

        etRenewalCode = findViewById(R.id.etRenewalCode)
        tvRenewalError = findViewById(R.id.tvRenewalError)
        btnRenew = findViewById(R.id.btnRenew)

        tvToggleRevoke = findViewById(R.id.tvToggleRevoke)
        layoutRevoke = findViewById(R.id.layoutRevoke)
        etRevokeCode = findViewById(R.id.etRevokeCode)
        btnRevoke = findViewById(R.id.btnRevoke)

        btnRenew.setOnClickListener {
            val code = etRenewalCode.text.toString().trim()

            if (code.isEmpty()) {
                showError("يرجى إدخال رمز التجديد")
                return@setOnClickListener
            }

            val success = LicenseManager.renewLicense(this, code)

            if (success) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            } else {
                showError("رمز التجديد غير صحيح")
            }
        }

        // Toggle revoke section visibility
        tvToggleRevoke.setOnClickListener {
            val isVisible = layoutRevoke.visibility == View.VISIBLE
            layoutRevoke.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        // Revoke button
        btnRevoke.setOnClickListener {
            val code = etRevokeCode.text.toString().trim()

            if (code.isEmpty()) {
                Toast.makeText(this, "يرجى إدخال رمز الإبطال", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val success = LicenseManager.revokeLicense(this, code)

            if (success) {
                Toast.makeText(this, "تم إبطال الترخيص على هذا الجهاز", Toast.LENGTH_SHORT).show()
                // Stay on this screen — license already cleared
                layoutRevoke.visibility = View.GONE
                etRevokeCode.text.clear()
            } else {
                Toast.makeText(this, "رمز الإبطال غير صحيح", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showError(message: String) {
        tvRenewalError.text = message
        tvRenewalError.visibility = View.VISIBLE
    }
}
