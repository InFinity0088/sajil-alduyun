package com.sajilalduyun.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sajilalduyun.app.R

object NotificationHelper {

    private const val CHANNEL_ID = "debt_alerts"
    private const val CHANNEL_NAME = "تنبيهات الديون"
    private const val BACKUP_CHANNEL_ID = "backup_reminders"
    private const val BACKUP_CHANNEL_NAME = "تذكيرات النسخ الاحتياطي"

    // Call this once when app starts to register the notification channels
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val debtChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات عند تجاوز حدود الديون"
            }
            val backupChannel = NotificationChannel(
                BACKUP_CHANNEL_ID,
                BACKUP_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تذكيرات يومية بالنسخ الاحتياطي"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(debtChannel)
            manager.createNotificationChannel(backupChannel)
        }
    }

    // Send a notification
    fun sendAlert(context: Context, title: String, message: String, notifId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
    }

    // Send backup reminder notification
    fun sendBackupReminder(context: Context, title: String, message: String, notifId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        val notification = NotificationCompat.Builder(context, BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
    }
}