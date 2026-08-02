package com.sajilalduyun.app.service

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.CustomerDebt
import com.sajilalduyun.app.model.DebtHistory
import com.sajilalduyun.app.model.DebtStatus
import com.sajilalduyun.app.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

object BackupService {

    private val dateFormat     = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmm",    Locale.getDefault())

    // ── TXT EXPORT (human-readable report) ──────────────────────────────────

    /**
     * Exports debts as a human-readable text report and opens the share sheet.
     */
    fun exportDebts(context: Context, debts: List<CustomerDebt>) {
        val sb = StringBuilder()
        val now = dateFormat.format(Date())

        sb.appendLine("===================================")
        sb.appendLine("       سجل الديون - تقرير الديون      ")
        sb.appendLine("===================================")
        sb.appendLine("تاريخ التصدير: $now")
        sb.appendLine("اجمالي السجلات: ${debts.size}")
        sb.appendLine()

        val approved = debts.count { it.status == DebtStatus.APPROVED }
        val pending  = debts.count { it.status == DebtStatus.PENDING  }
        val locked   = debts.count { it.status == DebtStatus.LOCKED   }

        sb.appendLine("الملخص:")
        sb.appendLine("  معتمد        : $approved")
        sb.appendLine("  قيد الانتظار : $pending")
        sb.appendLine("  مقفل         : $locked")
        sb.appendLine()
        sb.appendLine("-----------------------------------")

        if (debts.isEmpty()) {
            sb.appendLine("لا توجد ديون مسجلة.")
        } else {
            debts.forEachIndexed { index: Int, debt: CustomerDebt ->
                val planLabel = if (debt.planDurationDays > 0) {
                    if (debt.maxLimit > 0.0) "خطة ${debt.planDurationDays} يوم - حد ${String.format(Locale.US, "%,.0f", debt.maxLimit)}"
                    else "خطة ${debt.planDurationDays} يوم - بدون حد"
                } else {
                    if (debt.maxLimit > 0.0) "خطة مفتوحة - حد ${String.format(Locale.US, "%,.0f", debt.maxLimit)}"
                    else "خطة مفتوحة - بدون حد"
                }
                val statusLabel = when (debt.status) {
                    DebtStatus.APPROVED -> "معتمد"
                    DebtStatus.PENDING  -> "قيد الانتظار"
                    DebtStatus.LOCKED   -> "مقفل"
                }
                val createdAt = dateFormat.format(debt.createdAt)
                val amountFormatted = String.format(Locale.US, "%,.0f", debt.amount)

                sb.appendLine()
                sb.appendLine("${index + 1}. ${debt.customerName}")
                sb.appendLine("   المبلغ  : $amountFormatted د.ع")
                sb.appendLine("   الخطة   : $planLabel")
                sb.appendLine("   الحالة  : $statusLabel")
                sb.appendLine("   التاريخ : $createdAt")
                sb.appendLine("-----------------------------------")
            }
        }

        sb.appendLine()
        sb.appendLine("تم انشاء هذا التقرير بواسطة تطبيق سجل الديون")

        // Write UTF-8 file
        val fileName  = "sajil_alduyun_${fileNameFormat.format(Date())}.txt"
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        outputDir.mkdirs()
        val file = File(outputDir, fileName)

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write('﻿'.code)
                writer.write(sb.toString())
            }
        }

        shareFile(context, file, "text/plain", "تقرير الديون - سجل الديون")
    }

    // ── JSON EXPORT (machine-readable, importable) ──────────────────────────

    /**
     * Exports all data (debts + users + history) as a JSON backup file
     * that can be re-imported later via [importJson].
     */
    fun exportJson(context: Context, debts: List<CustomerDebt>, users: List<User>, history: List<DebtHistory>) {
        val root = JSONObject().apply {
            put("version", 1)
            put("exportedAt", dateFormat.format(Date()))
            put("debts", JSONArray(debts.map { it.toJson() }))
            put("users", JSONArray(users.map { it.toJson() }))
            put("debtHistory", JSONArray(history.map { it.toJson() }))
        }

        val fileName = "sajil_alduyun_backup_${fileNameFormat.format(Date())}.json"
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        outputDir.mkdirs()
        val file = File(outputDir, fileName)

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write(root.toString(2))
            }
        }

        shareFile(context, file, "application/json", "النسخ الاحتياطي - سجل الديون")
    }

    // ── JSON IMPORT ─────────────────────────────────────────────────────────

    data class ImportResult(
        val debtsImported: Int,
        val usersImported: Int,
        val historyImported: Int,
        val errors: List<String>
    )

    /**
     * Parses a JSON backup file and inserts all data into the database.
     * Must be called from a coroutine context (Room DAOs are suspend functions).
     */
    suspend fun importJson(context: Context, jsonString: String): ImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var debtsCount = 0
        var usersCount = 0
        var historyCount = 0

        try {
            // Strip BOM character (﻿) — it breaks org.json parser
            val cleanJson = jsonString.trimStart().let {
                if (it.isNotEmpty() && it[0].code == 0xFEFF) it.substring(1) else it
            }.trim()
            if (cleanJson.isBlank()) {
                errors.add("الملف فارغ — يرجى اختيار ملف النسخة الاحتياطية")
                return@withContext ImportResult(0, 0, 0, errors)
            }
            val root = JSONObject(cleanJson)
            // Verify it has the expected backup structure
            if (!root.has("version") && !root.has("debts")) {
                errors.add("هذا الملف ليس نسخة احتياطية صالحة. اختر ملف JSON الذي يصدره التطبيق")
                return@withContext ImportResult(0, 0, 0, errors)
            }

            // --- Import debts ---
            if (root.has("debts")) {
                val debtsArray = root.getJSONArray("debts")
                val db = AppDatabase.getDatabase(context)
                for (i in 0 until debtsArray.length()) {
                    try {
                        val obj = debtsArray.getJSONObject(i)
                        val debt = obj.toCustomerDebt()
                        db.debtDao().insertDebt(debt)
                        debtsCount++
                    } catch (e: Exception) {
                        errors.add("خطأ في استيراد الدين #$i: ${e.localizedMessage}")
                    }
                }
            }

            // --- Import users ---
            if (root.has("users")) {
                val usersArray = root.getJSONArray("users")
                val db = AppDatabase.getDatabase(context)
                for (i in 0 until usersArray.length()) {
                    try {
                        val obj = usersArray.getJSONObject(i)
                        val user = obj.toUser()
                        db.userDao().insertUser(user)
                        usersCount++
                    } catch (e: Exception) {
                        errors.add("خطأ في استيراد المستخدم #$i: ${e.localizedMessage}")
                    }
                }
            }

            // --- Import debt history ---
            if (root.has("debtHistory")) {
                val historyArray = root.getJSONArray("debtHistory")
                val db = AppDatabase.getDatabase(context)
                for (i in 0 until historyArray.length()) {
                    try {
                        val obj = historyArray.getJSONObject(i)
                        val entry = obj.toDebtHistory()
                        db.debtHistoryDao().insert(entry)
                        historyCount++
                    } catch (e: Exception) {
                        errors.add("خطأ في استيراد تاريخ الدين #$i: ${e.localizedMessage}")
                    }
                }
            }

        } catch (e: Exception) {
            errors.add("فشل قراءة ملف النسخ الاحتياطي: ${e.localizedMessage}")
        }

        ImportResult(debtsCount, usersCount, historyCount, errors)
    }

    // ── JSON serialization helpers ──────────────────────────────────────────

    private fun CustomerDebt.toJson() = JSONObject().apply {
        put("id", id)
        put("customerName", customerName)
        put("amount", amount)
        put("planId", planId)
        put("maxLimit", maxLimit)
        put("planDurationDays", planDurationDays)
        put("status", status.name)
        put("createdByUserId", createdByUserId)
        put("createdAt", createdAt.time)
        put("lastUpdatedAt", lastUpdatedAt.time)
    }

    private fun JSONObject.toCustomerDebt() = CustomerDebt(
        id = getString("id"),
        customerName = getString("customerName"),
        amount = getDouble("amount"),
        planId = getLong("planId"),
        maxLimit = getDouble("maxLimit"),
        planDurationDays = if (has("planDurationDays")) getInt("planDurationDays") else 0,
        status = DebtStatus.valueOf(getString("status")),
        createdByUserId = getString("createdByUserId"),
        createdAt = Date(getLong("createdAt")),
        lastUpdatedAt = if (has("lastUpdatedAt")) Date(getLong("lastUpdatedAt")) else Date()
    )

    private fun User.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("role", role.name)
        put("pin", pin)
        put("phoneNumber", phoneNumber)
        put("isActive", isActive)
    }

    private fun JSONObject.toUser() = User(
        id = getString("id"),
        name = getString("name"),
        role = com.sajilalduyun.app.model.UserRole.valueOf(getString("role")),
        pin = getString("pin"),
        phoneNumber = optString("phoneNumber", ""),
        isActive = getBoolean("isActive")
    )

    private fun DebtHistory.toJson() = JSONObject().apply {
        put("id", id)
        put("debtId", debtId)
        put("actionType", actionType)
        put("oldAmount", oldAmount)
        put("newAmount", newAmount)
        put("changedByUserId", changedByUserId)
        put("notes", notes)
        put("changedAt", changedAt.time)
    }

    private fun JSONObject.toDebtHistory() = DebtHistory(
        id = opt("id")?.toString() ?: "",  // handles both String UUID and legacy Long
        debtId = getString("debtId"),
        actionType = getString("actionType"),
        oldAmount = getDouble("oldAmount"),
        newAmount = getDouble("newAmount"),
        changedByUserId = getString("changedByUserId"),
        notes = optString("notes", ""),
        changedAt = Date(getLong("changedAt"))
    )

    // ── File sharing helper ─────────────────────────────────────────────────

    private fun shareFile(context: Context, file: File, mimeType: String, subject: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "مشاركة عبر...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /**
     * Reads the content of a content:// URI into a String.
     */
    fun readUriContent(context: Context, uri: android.net.Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).readText()
            }
        } catch (e: Exception) {
            null
        }
    }
}
