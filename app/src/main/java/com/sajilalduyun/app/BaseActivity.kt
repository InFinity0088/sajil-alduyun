package com.sajilalduyun.app

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sajilalduyun.app.security.SecurityManager

open class BaseActivity : AppCompatActivity() {

    protected var userId: String = ""
    protected var userRole: String = ""

    /**
     * Override in activities that should skip session expiry checks
     * (login, setup, license screens). Defaults to true.
     */
    open fun isSessionCheckRequired(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        super.onCreate(savedInstanceState)
        SecurityManager.init(applicationContext)
        userId = intent.getStringExtra("USER_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: ""
    }

    override fun onResume() {
        super.onResume()
        // Skip session check for login and setup screens
        if (!isSessionCheckRequired()) return
        if (SecurityManager.isSessionExpired()) {
            Toast.makeText(this, "انتهت جلستك، يرجى تسجيل الدخول من جديد", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        SecurityManager.recordActivity()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        SecurityManager.recordActivity()
        return super.dispatchTouchEvent(ev)
    }
}
