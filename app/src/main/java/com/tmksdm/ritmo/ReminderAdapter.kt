package com.tmksdm.ritmo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView

/** Строка списка: заголовок группы или само напоминание. */
sealed class ReminderRow {
    data class Group(
        val name: String?,
        val count: Int,
        val expanded: Boolean
    ) : ReminderRow()

    data class Item(val reminder: Reminder) : ReminderRow()
}

/** Список напоминаний: плоский или с разделами групп. */
class ReminderAdapter(
    private val context: Context,
    private val onToggle: (Reminder, Boolean) -> Unit,
    private val onClick: (Reminder) -> Unit,
    private val onMenu: (Reminder, View) -> Unit,
    private val onToggleGroup: (String?) -> Unit,
    private val onGroupMenu: (String?, View) -> Unit
) : BaseAdapter() {

    private val rows = mutableListOf<ReminderRow>()

    fun setRows(newRows: List<ReminderRow>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    fun rowAt(position: Int): ReminderRow = rows[position]

    override fun getCount(): Int = rows.size
    override fun getItem(position: Int): ReminderRow = rows[position]
    override fun getItemId(position: Int): Long = when (val row = rows[position]) {
        is ReminderRow.Item -> row.reminder.id
        is ReminderRow.Group -> -(position + 1L)
    }

    override fun getViewTypeCount(): Int = 2

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ReminderRow.Group -> TYPE_GROUP
        is ReminderRow.Item -> TYPE_REMINDER
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        when (val row = rows[position]) {
            is ReminderRow.Group -> groupView(row, convertView, parent)
            is ReminderRow.Item -> reminderView(row.reminder, convertView, parent)
        }

    private fun groupView(row: ReminderRow.Group, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_group_header, parent, false)
        view.findViewById<TextView>(R.id.group_name).apply {
            text = row.name ?: context.getString(R.string.group_ungrouped)
            setTextColor(context.themeColor(R.attr.ritmoTextPrimary))
        }
        view.findViewById<TextView>(R.id.group_count).apply {
            text = context.getString(R.string.group_count, row.count)
            setTextColor(context.themeColor(R.attr.ritmoTextSecondary))
        }
        view.findViewById<ImageView>(R.id.group_chevron).apply {
            rotation = if (row.expanded) 90f else 0f
            contentDescription = context.getString(
                if (row.expanded) R.string.group_collapse else R.string.group_expand
            )
        }
        val menu = view.findViewById<ImageButton>(R.id.group_menu)
        // У «Без группы» нет имени: переименовывать и удалять нечего.
        menu.visibility = if (row.name == null) View.GONE else View.VISIBLE
        menu.setOnClickListener { anchor -> onGroupMenu(row.name, anchor) }
        view.setOnClickListener { onToggleGroup(row.name) }
        return view
    }

    private fun reminderView(reminder: Reminder, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_reminder, parent, false)

        val title = view.findViewById<TextView>(R.id.title)
        title.text = reminder.title
        // Выключенное напоминание приглушено, как и в макете.
        title.setTextColor(
            context.themeColor(if (reminder.enabled) R.attr.ritmoTextPrimary else R.attr.ritmoTextSecondary)
        )
        view.findViewById<TextView>(R.id.subtitle).text = Formats.subtitle(context, reminder)

        val toggle = view.findViewById<Switch>(R.id.enabled)
        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = reminder.enabled
        toggle.setOnCheckedChangeListener { _, isChecked -> onToggle(reminder, isChecked) }

        val menu = view.findViewById<ImageButton>(R.id.item_menu)
        menu.setOnClickListener { anchor -> onMenu(reminder, anchor) }

        view.setOnClickListener { onClick(reminder) }
        return view
    }

    private companion object {
        const val TYPE_GROUP = 0
        const val TYPE_REMINDER = 1
    }
}
