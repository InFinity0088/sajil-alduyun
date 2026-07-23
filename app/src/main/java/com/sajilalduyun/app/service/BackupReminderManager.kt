package com.sajilalduyun.app.service

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import java.util.Calendar

object BackupReminderManager {

    private const val BACKUP_REMINDER_TAG = "backup_reminder_daily"
    private const val BACKUP_REMINDER_ID = "backup_reminder_worker"

    fun scheduleBackupReminder(context: Context) {
        val backupWorker = PeriodicWorkRequestBuilder<BackupReminderWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(calculateInitialDelay(), TimeUnit.MINUTES)
            .addTag(BACKUP_REMINDER_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BACKUP_REMINDER_ID,
            ExistingPeriodicWorkPolicy.KEEP,
            backupWorker
        )
    }

    fun cancelBackupReminder(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(BACKUP_REMINDER_TAG)
    }

    private fun calculateInitialDelay(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        if (now.after(target)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val delayMs = target.timeInMillis - now.timeInMillis
        return delayMs / 60000 // Convert to minutes
    }
}
