package com.tmksdm.ritmo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast

/** Главный экран: список напоминаний, при наличии групп — разделы со сворачиванием. */
class MainActivity : Activity() {

    private lateinit var adapter: ReminderAdapter
    private var reminders: MutableList<Reminder> = mutableListOf()
    private var definedGroups: MutableList<String> = mutableListOf()
    private var exactAlarmDialogShown = false
    private var lastExactAlarmState: Boolean? = null
    private var appliedTheme = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = ThemePrefs.themeResId(this)
        setTheme(appliedTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).applySystemBarInsets()

        adapter = ReminderAdapter(
            this,
            ::toggleReminder,
            ::editReminder,
            ::showItemMenu,
            ::toggleGroup,
            ::showGroupMenu
        )
        val listView = findViewById<ListView>(R.id.reminder_list)
        listView.adapter = adapter
        listView.emptyView = findViewById<TextView>(R.id.empty_view)
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val row = adapter.rowAt(position)
            if (row is ReminderRow.Item) {
                confirmDelete(row.reminder)
                true
            } else {
                false
            }
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
        reminders = ReminderStore.load(this)
        definedGroups = Groups.load(this).toMutableList()
        val list = ReminderGroups.buildList(
            reminders,
            definedGroups,
            System.currentTimeMillis(),
            { Groups.isExpanded(this, it) }
        )
        adapter.setRows(rowsFor(list))
    }

    private fun rowsFor(list: ReminderList): List<ReminderRow> = buildList {
        for (section in list.sections) {
            if (list.grouped) {
                add(ReminderRow.Group(section.name, section.reminders.size, section.expanded))
            }
            if (!list.grouped || section.expanded) {
                section.reminders.forEach { add(ReminderRow.Item(it)) }
            }
        }
    }

    private fun editReminder(reminder: Reminder?) {
        startActivity(Intent(this, EditActivity::class.java).apply {
            if (reminder != null) putExtra(EditActivity.EXTRA_ID, reminder.id)
        })
    }

    private fun showItemMenu(reminder: Reminder, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(Menu.NONE, MENU_EDIT, 0, R.string.action_edit)
        popup.menu.add(Menu.NONE, MENU_MOVE, 1, R.string.group_move)
        popup.menu.add(Menu.NONE, MENU_DELETE, 2, R.string.delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EDIT -> {
                    editReminder(reminder)
                    true
                }

                MENU_MOVE -> {
                    showMoveDialog(reminder)
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

    /** Диалог перемещения: «Без группы», существующие группы и создание новой. */
    private fun showMoveDialog(reminder: Reminder) {
        val groups = groupNames()
        val labels = mutableListOf(getString(R.string.group_ungrouped))
        labels += groups.map { name ->
            if (name == reminder.group) getString(R.string.group_current, name) else name
        }
        labels += getString(R.string.group_new)
        AlertDialog.Builder(this)
            .setTitle(R.string.group_move_title)
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which == 0 -> assignGroup(reminder, null)
                    which <= groups.size -> assignGroup(reminder, groups[which - 1])
                    else -> promptNewGroup { name -> assignGroup(reminder, name) }
                }
            }
            .show()
    }

    private fun assignGroup(reminder: Reminder, rawName: String?) {
        val old = reminder.group
        val target = rawName?.trim()?.takeIf { it.isNotEmpty() }
        if (old == target) {
            reload()
            return
        }
        reminder.group = target
        if (!ReminderStore.save(this, reminders)) {
            reminder.group = old
            Toast.makeText(this, R.string.save_error, Toast.LENGTH_LONG).show()
            reload()
            return
        }
        if (target != null && definedGroups.none { it.equals(target, ignoreCase = true) }) {
            GroupStoreSupport.addAndSave(this, definedGroups, target)
        }
        reload()
    }

    private fun promptNewGroup(onName: (String) -> Unit) {
        val input = groupNameInput("")
        AlertDialog.Builder(this)
            .setTitle(R.string.group_new_title)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.group_empty_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (name.equals(getString(R.string.group_ungrouped), ignoreCase = true)) {
                    Toast.makeText(this, R.string.group_exists, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // Если группа уже есть, используем её каноническое название.
                onName(groupNames().firstOrNull { it.equals(name, ignoreCase = true) } ?: name)
            }
            .show()
    }

    private fun showGroupMenu(group: String?, anchor: View) {
        if (group == null) return
        val popup = PopupMenu(this, anchor)
        popup.menu.add(Menu.NONE, MENU_RENAME_GROUP, 0, R.string.group_rename)
        popup.menu.add(Menu.NONE, MENU_DELETE_GROUP, 1, R.string.delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_RENAME_GROUP -> {
                    promptRenameGroup(group)
                    true
                }

                MENU_DELETE_GROUP -> {
                    confirmDeleteGroup(group)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun promptRenameGroup(oldName: String) {
        val input = groupNameInput(oldName)
        AlertDialog.Builder(this)
            .setTitle(R.string.group_rename_title)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                when {
                    name.isEmpty() ->
                        Toast.makeText(this, R.string.group_empty_name, Toast.LENGTH_SHORT).show()

                    name.equals(getString(R.string.group_ungrouped), ignoreCase = true) ->
                        Toast.makeText(this, R.string.group_exists, Toast.LENGTH_SHORT).show()

                    groupNames().any { !it.equals(oldName, true) && it.equals(name, true) } ->
                        Toast.makeText(this, R.string.group_exists, Toast.LENGTH_SHORT).show()

                    else -> renameGroup(oldName, name)
                }
            }
            .show()
    }

    /** Переименование меняет только название группы у её напоминаний. */
    private fun renameGroup(oldName: String, name: String) {
        val affected = reminders.filter { it.group?.equals(oldName, ignoreCase = true) == true }
        affected.forEach { it.group = name }
        if (!ReminderStore.save(this, reminders)) {
            affected.forEach { it.group = oldName }
            Toast.makeText(this, R.string.save_error, Toast.LENGTH_LONG).show()
            reload()
            return
        }
        val index = definedGroups.indexOfFirst { it.equals(oldName, ignoreCase = true) }
        if (index >= 0) definedGroups[index] = name else GroupStoreSupport.add(definedGroups, name)
        GroupStoreSupport.save(this, definedGroups)
        Groups.renameExpanded(this, oldName, name)
        reload()
    }

    /** Удаление группы снимает назначение, но не удаляет напоминания. */
    private fun confirmDeleteGroup(name: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.group_delete_title)
            .setMessage(getString(R.string.group_delete_message, name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val affected = reminders.filter { it.group?.equals(name, ignoreCase = true) == true }
                affected.forEach { it.group = null }
                if (!ReminderStore.save(this, reminders)) {
                    affected.forEach { it.group = name }
                    Toast.makeText(this, R.string.save_error, Toast.LENGTH_LONG).show()
                    reload()
                    return@setPositiveButton
                }
                definedGroups.removeAll { it.equals(name, ignoreCase = true) }
                GroupStoreSupport.save(this, definedGroups)
                Groups.forgetExpanded(this, name)
                reload()
            }
            .show()
    }

    private fun toggleGroup(name: String?) {
        Groups.setExpanded(this, name, !Groups.isExpanded(this, name))
        reload()
    }

    /** Все группы: сохранённые и добавленные назначениями из резервной копии. */
    private fun groupNames(): List<String> {
        val names = mutableListOf<String>()
        definedGroups.forEach { GroupStoreSupport.add(names, it) }
        reminders.forEach { reminder -> reminder.group?.let { GroupStoreSupport.add(names, it) } }
        return names
    }

    private fun groupNameInput(value: String): EditText = EditText(this).apply {
        hint = getString(R.string.group_name_hint)
        filters = arrayOf(InputFilter.LengthFilter(MAX_GROUP_NAME_LENGTH))
        setText(value)
        setSelection(text.length)
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
        const val MENU_MOVE = 2
        const val MENU_DELETE = 3
        const val MENU_RENAME_GROUP = 4
        const val MENU_DELETE_GROUP = 5
        const val MAX_GROUP_NAME_LENGTH = 60
    }
}

/** Небольшие операции со списком групп и его записью, чтобы не дублировать их в диалогах. */
private object GroupStoreSupport {

    fun add(names: MutableList<String>, raw: String): Boolean = Groups.addName(names, raw)

    fun addAndSave(activity: Activity, names: MutableList<String>, raw: String) {
        if (add(names, raw) && !Groups.save(activity, names)) {
            Toast.makeText(activity, R.string.save_error, Toast.LENGTH_LONG).show()
        }
    }

    fun save(activity: Activity, names: List<String>) {
        if (!Groups.save(activity, names)) {
            Toast.makeText(activity, R.string.save_error, Toast.LENGTH_LONG).show()
        }
    }
}
