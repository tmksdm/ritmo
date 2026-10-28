package com.tmksdm.ritmo

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Единый список напоминаний: без секций групп, с тегами возле переключателя. */
class ReminderAdapter(
    private val context: Context,
    private val onToggle: (Reminder, Boolean) -> Unit,
    private val onClick: (Reminder) -> Unit,
    private val onMenu: (Reminder, View) -> Unit,
    private val onTagClick: (String) -> Unit
) : BaseAdapter() {

    private var items: List<Reminder> = emptyList()
    private var activeTag: String? = null

    fun setReminders(newItems: List<Reminder>, tag: String?) {
        items = newItems
        activeTag = tag
        notifyDataSetChanged()
    }

    fun reminderAt(position: Int): Reminder = items[position]

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Reminder = items[position]
    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val reminder = items[position]
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_reminder, parent, false)

        val title = view.findViewById<TextView>(R.id.title)
        title.text = reminder.title
        // Выключенное напоминание приглушено, как и в макете.
        title.setTextColor(
            context.themeColor(if (reminder.enabled) R.attr.ritmoTextPrimary else R.attr.ritmoTextSecondary)
        )
        view.findViewById<TextView>(R.id.subtitle).text = Formats.subtitle(context, reminder)

        bindTags(
            view.findViewById(R.id.tags_scroll),
            view.findViewById(R.id.tags_container),
            reminder
        )

        val toggle = view.findViewById<Switch>(R.id.enabled)
        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = reminder.enabled
        toggle.setOnCheckedChangeListener { _, isChecked -> onToggle(reminder, isChecked) }

        val menu = view.findViewById<ImageButton>(R.id.item_menu)
        menu.setOnClickListener { anchor -> onMenu(reminder, anchor) }

        view.setOnClickListener { onClick(reminder) }
        return view
    }

    private fun bindTags(scroll: View, container: LinearLayout, reminder: Reminder) {
        container.removeAllViews()
        if (reminder.tags.isEmpty()) {
            scroll.visibility = View.GONE
            return
        }
        scroll.visibility = View.VISIBLE
        reminder.tags.forEach { tag ->
            container.addView(
                tagChip(tag).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = context.dp(6) }
                }
            )
        }
    }

    /**
     * Тег — переключаемый чип в стиле дней недели. Нажатие включает или снимает
     * фильтр; повторное нажатие выбранного тега сбрасывает его.
     */
    private fun tagChip(tag: String): CheckBox = CheckBox(context).apply {
        text = tag
        isChecked = TagNames.same(activeTag, tag)
        setButtonDrawable(null)
        background = context.getDrawable(R.drawable.segment_background)
        setTextColor(context.resources.getColorStateList(R.color.chip_text, context.theme))
        gravity = Gravity.CENTER
        minHeight = context.dp(28)
        setPadding(context.dp(8), 0, context.dp(8), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        contentDescription = context.getString(R.string.tag_filter_action, tag)
        setOnClickListener { onTagClick(tag) }
    }

    private fun Context.dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()
}
