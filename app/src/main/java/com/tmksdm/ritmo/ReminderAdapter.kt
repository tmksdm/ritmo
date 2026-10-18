package com.tmksdm.ritmo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Switch
import android.widget.TextView

/** Список напоминаний на главном экране. */
class ReminderAdapter(
    private val context: Context,
    private val onToggle: (Reminder, Boolean) -> Unit,
    private val onClick: (Reminder) -> Unit
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

        view.findViewById<TextView>(R.id.title).text = reminder.title
        view.findViewById<TextView>(R.id.subtitle).text = Formats.subtitle(reminder)

        val toggle = view.findViewById<Switch>(R.id.enabled)
        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = reminder.enabled
        toggle.setOnCheckedChangeListener { _, isChecked -> onToggle(reminder, isChecked) }

        view.setOnClickListener { onClick(reminder) }
        return view
    }
}
