package com.tmksdm.ritmo

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast

/** Главный экран: единый список напоминаний, теги и фильтр по тегу. */
class MainActivity : Activity() {

    private lateinit var adapter: ReminderAdapter
    private lateinit var emptyView: TextView
    private lateinit var filterBar: View
    private lateinit var filterLabel: TextView
    private var reminders: MutableList<Reminder> = mutableListOf()
    private var definedTags: MutableList<String> = mutableListOf()
    private var activeTag: String? = null
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
            ::toggleTagFilter
        )
        val listView = findViewById<ListView>(R.id.reminder_list)
        listView.adapter = adapter
        emptyView = findViewById(R.id.empty_view)
        listView.emptyView = emptyView
        listView.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(adapter.reminderAt(position))
            true
        }
        filterBar = findViewById(R.id.filter_bar)
        filterLabel = findViewById(R.id.filter_label)
        findViewById<View>(R.id.filter_reset).setOnClickListener {
            activeTag = null
            reload()
        }
        findViewById<View>(R.id.tags_button).setOnClickListener { showTagManager() }
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
        Tags.migrateLegacyGroups(this, reminders)
        definedTags = Tags.all(this, reminders)
        if (activeTag != null && !hasTag(activeTag!!)) activeTag = null

        val visible = activeTag?.let { ReminderOrdering.withTag(reminders, it) } ?: reminders
        adapter.setReminders(
            ReminderOrdering.ordered(visible, System.currentTimeMillis()),
            activeTag
        )
        filterBar.visibility = if (activeTag == null) View.GONE else View.VISIBLE
        filterLabel.text = activeTag?.let { getString(R.string.tag_filter_active, it) }.orEmpty()
        emptyView.text = if (activeTag != null && reminders.isNotEmpty()) {
            getString(R.string.empty_filter, activeTag)
        } else {
            getString(R.string.empty_list)
        }
    }

    private fun hasTag(tag: String): Boolean =
        definedTags.any { TagNames.same(it, tag) } ||
            reminders.any { TagNames.contains(it.tags, tag) }

    private fun toggleTagFilter(tag: String) {
        activeTag = if (TagNames.same(activeTag, tag)) null else tag
        reload()
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

    /** Список тегов и создание нового; действия над тегом — во вложенном диалоге. */
    private fun showTagManager() {
        val tags = currentTags()
        val labels = tags.toMutableList()
        labels.add(getString(R.string.tag_new))
        AlertDialog.Builder(this)
            .setTitle(R.string.tags_title)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == tags.size) promptTagName(null) else showTagActions(tags[which])
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showTagActions(tag: String) {
        AlertDialog.Builder(this)
            .setTitle(tag)
            .setItems(arrayOf(getString(R.string.tag_rename), getString(R.string.delete))) { _, which ->
                when (which) {
                    0 -> promptTagName(tag)
                    1 -> confirmDeleteTag(tag)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptTagName(oldName: String?) {
        val (view, input) = tagNameDialog(oldName.orEmpty())
        AlertDialog.Builder(this)
            .setTitle(if (oldName == null) R.string.tag_new_title else R.string.tag_rename_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = TagNames.normalize(input.text.toString())
                when {
                    name == null -> toast(R.string.tag_empty_name)

                    TagNames.isTaken(currentTags(), name, oldName) -> toast(R.string.tag_exists)

                    oldName == null -> createTag(name)

                    else -> renameTag(oldName, name)
                }
            }
            .show()
    }

    /** Общий вид диалога: отступы контейнера совпадают с заголовком, поле выделяет текст. */
    @SuppressLint("InflateParams")
    private fun tagNameDialog(value: String): Pair<View, EditText> {
        val view = layoutInflater.inflate(R.layout.dialog_name_input, null)
        val input = view.findViewById<EditText>(R.id.name_input)
        input.setText(value)
        return view to input
    }

    private fun createTag(name: String) {
        val before = definedTags.toList()
        TagNames.add(definedTags, name)
        if (!Tags.save(this, definedTags)) {
            definedTags = before.toMutableList()
            toast(R.string.save_error)
            return
        }
        reload()
    }

    /** Переименование меняет название тега и у всех напоминаний. */
    private fun renameTag(oldName: String, name: String) {
        val beforeTags = definedTags.toList()
        val nextTags = beforeTags.toMutableList()
        val index = nextTags.indexOfFirst { TagNames.same(it, oldName) }
        if (index >= 0) nextTags[index] = name else TagNames.add(nextTags, name)
        val affected = reminders.filter { TagNames.contains(it.tags, oldName) }
        val snapshots = affected.map { it to it.tags.toList() }
        affected.forEach { reminder ->
            reminder.tags = TagNames.normalizeAll(
                reminder.tags.map { if (TagNames.same(it, oldName)) name else it }
            )
        }
        // Сначала сохраняются определения: при ошибке Tags.save назначения не меняются.
        if (!Tags.saveEdit(this, reminders, beforeTags, nextTags)) {
            snapshots.forEach { (reminder, tags) -> reminder.tags = tags.toMutableList() }
            toast(R.string.save_error)
            reload()
            return
        }
        definedTags = nextTags
        if (TagNames.same(activeTag, oldName)) activeTag = name
        reload()
    }

    /** Удаление тега снимает его у напоминаний, но не удаляет напоминания. */
    private fun confirmDeleteTag(tag: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.tag_delete_title)
            .setMessage(getString(R.string.tag_delete_message, tag))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val beforeTags = definedTags.toList()
                val nextTags = beforeTags.filterNot { TagNames.same(it, tag) }.toMutableList()
                val affected = reminders.filter { TagNames.contains(it.tags, tag) }
                val snapshots = affected.map { it to it.tags.toList() }
                affected.forEach { reminder ->
                    reminder.tags = TagNames.normalizeAll(
                        reminder.tags.filterNot { TagNames.same(it, tag) }
                    )
                }
                // Сначала сохраняются определения: при ошибке Tags.save назначения не меняются.
                if (!Tags.saveEdit(this, reminders, beforeTags, nextTags)) {
                    snapshots.forEach { (reminder, tags) -> reminder.tags = tags.toMutableList() }
                    toast(R.string.save_error)
                    reload()
                    return@setPositiveButton
                }
                definedTags = nextTags
                if (TagNames.same(activeTag, tag)) activeTag = null
                reload()
            }
            .show()
    }

    /** Все теги: объявленные и встретившиеся в напоминаниях (старые данные, импорт). */
    private fun currentTags(): MutableList<String> = TagNames.merge(
        definedTags,
        reminders.asSequence().flatMap { it.tags.asSequence() }.toList()
    )

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        const val MENU_DELETE = 3
    }
}
