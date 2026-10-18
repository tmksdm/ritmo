package com.tmksdm.ritmo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView

/** Главный экран: список напоминаний. */
class MainActivity : Activity() {

    private lateinit var adapter: ReminderAdapter
    private var reminders: MutableList<Reminder> = mutableListOf()
    private var exactAlarmDialogShown = false
    private var lastExactAlarmState: Boolean? = null
    private var appliedTheme = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = ThemePrefs.themeResId(this)
        setTheme(appliedTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).applySystemBarInsets()

        adapter = ReminderAdapter(this, ::toggleReminder, ::editReminder)
        val listView = findViewById<ListView>(R.id.reminder_list)
        listView.adapter = adapter
        listView.emptyView = findViewById<TextView>(R.id.empty_view)
        listView.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(adapter.getItem(position))
            true
        }
        findViewById<Button>(R.id.add_button).setOnClickListener { editReminder(null) }
        findViewById<Button>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (appliedTheme != ThemePrefs.themeResId(this)) {
            // Пользователь сменил тему в настройках — пересоздаём экран.
            recreate()
            return
        }
        reload()
        handleExactAlarmState()
    }

    private fun reload() {
        reminders = ReminderStore.load(this)
        adapter.setItems(reminders)
    }

    private fun editReminder(reminder: Reminder?) {
        startActivity(Intent(this, EditActivity::class.java).apply {
            if (reminder != null) putExtra(EditActivity.EXTRA_ID, reminder.id)
        })
    }

    private fun toggleReminder(reminder: Reminder, enabled: Boolean) {
        reminder.enabled = enabled
        ReminderStore.save(this, reminders)
        if (enabled) AlarmScheduler.schedule(this, reminder) else AlarmScheduler.cancel(this, reminder)
        reload()
    }

    private fun confirmDelete(reminder: Reminder) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, reminder.title))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                AlarmScheduler.cancel(this, reminder)
                reminders.removeAll { it.id == reminder.id }
                ReminderStore.save(this, reminders)
                reload()
            }
            .show()
    }

    private fun handleExactAlarmState() {
        val exact = AlarmScheduler.canScheduleExact(this)
        if (lastExactAlarmState == false && exact) {
            // Доступ выдан: перепланируем активные напоминания в точном режиме.
            AlarmScheduler.rescheduleAll(this)
        }
        lastExactAlarmState = exact
        warnAboutExactAlarmsIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.canPostNotifications(this)
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun warnAboutExactAlarmsIfNeeded() {
        if (exactAlarmDialogShown || AlarmScheduler.canScheduleExact(this)) return
        exactAlarmDialogShown = true
        AlertDialog.Builder(this)
            .setTitle(R.string.exact_alarm_title)
            .setMessage(R.string.exact_alarm_message)
            .setNegativeButton(R.string.later, null)
            .setPositiveButton(R.string.allow) { _, _ -> AlarmScheduler.requestExactAlarmAccess(this) }
            .show()
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 100
    }
}
