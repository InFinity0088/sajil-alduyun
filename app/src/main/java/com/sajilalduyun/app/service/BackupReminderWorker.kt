package com.sajilalduyun.app.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sajilalduyun.app.database.AppDatabase
import kotlinx.coroutines.runBlocking

class BackupReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val owner = runBlocking {
                db.userDao().getOwner()
            }

            if (owner != null && owner.isActive) {
                NotificationHelper.sendBackupReminder(
                    applicationContext,
                    "حان وقت النسخ الاحتياطي",
                    "يرجى النسخ الاحتياطي من بيانات سجل الديون الخاص بك",
                    9001
                )
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
