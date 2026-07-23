package com.sajilalduyun.app.ui

import android.app.ProgressDialog
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sajilalduyun.app.R

class LottieOverlayManager(private val activity: AppCompatActivity) {
    private var overlayContainer: FrameLayout? = null
    private var loadingView: View? = null
    private var successView: View? = null
    private var progressDialog: ProgressDialog? = null

    fun initialize(rootView: ViewGroup) {
        overlayContainer = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootView.addView(overlayContainer)

        // Loading view - simple text based
        loadingView = LinearLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#0D211799"))
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE

            val textView = TextView(activity).apply {
                text = "جاري الحفظ..."
                setTextColor(android.graphics.Color.parseColor("#CFFF04"))
                textSize = 16f
            }
            addView(textView)
        }

        // Success view - simple text based
        successView = LinearLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#0D211799"))
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE

            val textView = TextView(activity).apply {
                text = "✓ تم الحفظ بنجاح!"
                setTextColor(android.graphics.Color.parseColor("#CFFF04"))
                textSize = 18f
            }
            addView(textView)
        }

        overlayContainer?.addView(loadingView)
        overlayContainer?.addView(successView)
    }

    fun showLoading() {
        loadingView?.visibility = View.VISIBLE
    }

    fun hideLoading() {
        loadingView?.visibility = View.GONE
    }

    fun showSuccess(durationMs: Long = 2000) {
        successView?.visibility = View.VISIBLE

        successView?.postDelayed({
            hideSuccess()
        }, durationMs)
    }

    fun hideSuccess() {
        successView?.visibility = View.GONE
    }
}

