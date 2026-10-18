package com.tmksdm.ritmo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Срабатывание будильника: показывает системное уведомление и планирует следующее. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0L) return

        val reminders = ReminderStore.load(context)
        val reminder = reminders.firstOrNull { it.id == id } ?: return
        if (!reminder.enabled) return

        val now = System.currentTimeMillis()
        // Защита от повторной обработки одного и того же срабатывания.
        if (reminder.lastTriggeredAt > 0 && now - reminder.lastTriggeredAt < DUPLICATE_WINDOW_MS) return

        NotificationHelper.show(context, reminder)

        reminder.lastTriggeredAt = now
        if (reminder.repeatType == RepeatType.ONCE) {
            // Однократное напоминание выполнено; смахивание уведомления на это не влияет.
            reminder.enabled = false
        }
        ReminderStore.save(context, reminders)

        // Следующее срабатывание считается от текущего момента: пропущенные не накапливаются.
        if (reminder.enabled) AlarmScheduler.schedule(context, reminder)
    }

    companion object {
        const val ACTION_TRIGGER = "com.tmksdm.ritmo.action.TRIGGER"
        const val EXTRA_ID = "reminder_id"
        private const val DUPLICATE_WINDOW_MS = 5_000L
    }
}
