package com.tmksdm.ritmo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

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

        adapter = ReminderAdapter(this, ::toggleReminder, ::editReminder, ::showItemMenu)
        val listView = findViewById<ListView>(R.id.reminder_list)
        listView.adapter = adapter
        listView.emptyView = findViewById<TextView>(R.id.empty_view)
        listView.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(adapter.getItem(position))
            true
        }
        findViewById<Button>(R.id.add_button).setOnClickListener { editReminder(null) }
        findViewById<ImageButton>(R.id.settings_button).setOnClickListener {
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
        reminders = ReminderStore.load(this).sortedWith(displayOrder).toMutableList()
        adapter.setItems(reminders)
    }

    /** Активные — по ближайшему срабатыванию, затем выключенные, затем завершённые. */
    private val displayOrder = compareBy<Reminder>(
        { displayGroup(it) },
        { displayTime(it) },
        { it.title.lowercase(Locale.getDefault()) }
    )

    private fun displayGroup(reminder: Reminder): Int = when {
        !reminder.enabled && reminder.repeatType == RepeatType.ONCE && reminder.lastTriggeredAt > 0 -> 2
        !reminder.enabled -> 1
        else -> 0
    }

    private fun displayTime(reminder: Reminder): Long = when (displayGroup(reminder)) {
        0 -> reminder.nextTriggerAt() ?: Long.MAX_VALUE
        else -> -reminder.dateTimeMillis
    }

    private fun editReminder(reminder: Reminder?) {
        startActivity(Intent(this, EditActivity::class.java).apply {
            if (reminder != null) putExtra(EditActivity.EXTRA_ID, reminder.id)
        })
    }

    private fun showItemMenu(reminder: Reminder, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(Menu.NONE, MENU_EDIT, 0, R.string.action_edit)
        popup.menu.add(Menu.NONE, MENU_DELETE, 1, R.string.delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EDIT -> {
                    editReminder(reminder)
                    true
                }

                MENU_DELETE -> {
                    confirmDelete(reminder)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun toggleReminder(reminder: Reminder, enabled: Boolean) {
        if (enabled && reminder.nextTriggerAt() == null) {
            // Включать нечего: у однократного напоминания время уже прошло,
            // у повторяющегося не заданы параметры расписания.
            Toast.makeText(this, R.string.error_schedule, Toast.LENGTH_LONG).show()
            reload()
            return
        }
        reminder.enabled = enabled
        if (ReminderStore.save(this, reminders)) {
            if (enabled) AlarmScheduler.schedule(this, reminder) else AlarmScheduler.cancel(this, reminder)
        } else {
            Toast.makeText(this, R.string.save_error, Toast.LENGTH_LONG).show()
        }
        // При неудачной записи reload() вернёт состояние, которое реально лежит на диске.
        reload()
    }

    private fun confirmDelete(reminder: Reminder) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, reminder.title))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val remaining = reminders.filterNot { it.id == reminder.id }
                if (ReminderStore.save(this, remaining)) {
                    AlarmScheduler.cancel(this, reminder)
                } else {
                    Toast.makeText(this, R.string.save_error, Toast.LENGTH_LONG).show()
                }
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
        const val MENU_EDIT = 1
        const val MENU_DELETE = 2
    }
}
