package com.tmksdm.ritmo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ListView
import android.widget.TextView

/** Главный экран: список напоминаний. */
class MainActivity : Activity() {

    private lateinit var adapter: ReminderAdapter
    private var reminders: MutableList<Reminder> = mutableListOf()
    private var exactAlarmDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = getString(R.string.app_name)

        adapter = ReminderAdapter(this, ::toggleReminder, ::editReminder)
        val listView = findViewById<ListView>(R.id.reminder_list)
        listView.adapter = adapter
        listView.emptyView = findViewById<TextView>(R.id.empty_view)
        listView.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(adapter.getItem(position))
            true
        }
        findViewById<Button>(R.id.add_button).setOnClickListener { editReminder(null) }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        reload()
        warnAboutExactAlarmsIfNeeded()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, R.string.settings)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_SETTINGS) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
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
        const val MENU_SETTINGS = 1
        const val REQUEST_NOTIFICATIONS = 100
    }
}
