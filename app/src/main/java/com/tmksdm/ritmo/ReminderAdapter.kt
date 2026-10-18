package com.tmksdm.ritmo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView

/** Список напоминаний на главном экране. */
class ReminderAdapter(
    private val context: Context,
    private val onToggle: (Reminder, Boolean) -> Unit,
    private val onClick: (Reminder) -> Unit,
    private val onMenu: (Reminder, View) -> Unit
) : BaseAdapter() {

    private val items = mutableListOf<Reminder>()

    fun setItems(reminders: List<Reminder>) {
        items.clear()
        items.addAll(reminders)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Reminder = items[position]
    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_reminder, parent, false)
        val reminder = getItem(position)

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
}
