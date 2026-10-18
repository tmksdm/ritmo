package com.tmksdm.ritmo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Планирование точных системных будильников.
 *
 * Используется setExactAndAllowWhileIdle(). Если пользователь не выдал доступ к точным
 * будильникам (Android 12+), применяется setAndAllowWhileIdle() — система может задержать
 * такое уведомление, о чём приложение предупреждает пользователя в интерфейсе.
 */
object AlarmScheduler {

    fun canScheduleExact(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context, reminder)
        val triggerAt = reminder.nextTriggerAt()
        if (!reminder.enabled || triggerAt == null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            return
        }
        try {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context, reminder)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** Перепланирование всех активных напоминаний (загрузка, смена времени, обновление APK). */
    fun rescheduleAll(context: Context) {
        for (reminder in ReminderStore.load(context)) {
            if (reminder.enabled) schedule(context, reminder)
        }
    }

    private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER
            data = Uri.parse("ritmo://reminder/${reminder.id}")
            putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Открывает системный экран выдачи доступа к точным будильникам (Android 12+). */
    fun requestExactAlarmAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { context.startActivity(intent) }
    }
}
