package com.tmksdm.ritmo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Срабатывание будильника: показывает системное уведомление. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0L) return

        val reminders = ReminderStore.load(context)
        val reminder = reminders.firstOrNull { it.id == id } ?: return
        if (!reminder.enabled) return

        NotificationHelper.show(context, reminder)

        if (reminder.repeatType == RepeatType.ONCE) {
            // Однократное напоминание выполнено: смахивание уведомления не влияет на это.
            reminder.enabled = false
            reminder.lastTriggeredAt = System.currentTimeMillis()
            ReminderStore.save(context, reminders)
        }
        // Повторяющиеся напоминания будут перепланированы в Этапе 2.
    }

    companion object {
        const val ACTION_TRIGGER = "com.tmksdm.ritmo.action.TRIGGER"
        const val EXTRA_ID = "reminder_id"
    }
}
