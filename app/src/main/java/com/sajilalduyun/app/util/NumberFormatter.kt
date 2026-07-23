package com.sajilalduyun.app.util

import java.text.DecimalFormat

object NumberFormatter {
    // DecimalFormat is NOT thread-safe, so we create a new instance per call
    private fun createFormatter() = DecimalFormat("#,###")

    fun formatWithCommas(number: Double): String {
        return createFormatter().format(number.toLong())
    }

    fun formatWithCommas(number: Long): String {
        return createFormatter().format(number)
    }

    fun formatWithCommas(number: Int): String {
        return createFormatter().format(number.toLong())
    }

    fun removeCommas(text: String): String {
        return text.replace(",", "")
    }
}
