package com.tmksdm.ritmo

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** Создание и редактирование напоминания: все шесть видов расписания. */
class EditActivity : Activity() {

    private enum class Preset { NONE, IN_15_MIN, IN_1_HOUR, TOMORROW_MORNING }

    private lateinit var titleInput: EditText
    private lateinit var repeatSpinner: Spinner
    private lateinit var dateTimeBlock: LinearLayout
    private lateinit var dateTimeLabel: TextView
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var timeBlock: LinearLayout
    private lateinit var timeButtonOnly: Button
    private lateinit var weeklyBlock: LinearLayout
    private lateinit var weekDayChips: List<CheckBox>
    private lateinit var monthlyBlock: LinearLayout
    private lateinit var monthDayPicker: NumberPicker
    private lateinit var intervalBlock: LinearLayout
    private lateinit var intervalPicker: NumberPicker
    private lateinit var intervalUnitSpinner: Spinner
    private lateinit var windowSwitch: Switch
    private lateinit var windowRow: LinearLayout
    private lateinit var windowStartButton: Button
    private lateinit var windowEndButton: Button
    private lateinit var everyNBlock: LinearLayout
    private lateinit var everyDaysPicker: NumberPicker
    private lateinit var note: TextView
    private lateinit var scheduleValue: TextView
    private lateinit var presetsBlock: LinearLayout
    private lateinit var presetButtons: List<Button>

    private val calendar: Calendar = Calendar.getInstance()
    private val windowStart: Calendar = Calendar.getInstance()
    private val windowEnd: Calendar = Calendar.getInstance()

    private var existing: Reminder? = null
    private var lastType: RepeatType = RepeatType.ONCE
    private var preset = Preset.NONE
    private var appliedTheme = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = ThemePrefs.themeResId(this)
        setTheme(appliedTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        findViewById<View>(R.id.root).applySystemBarInsets()

        bindViews()
        windowStart.set(Calendar.HOUR_OF_DAY, 9)
        windowStart.set(Calendar.MINUTE, 0)
        windowEnd.set(Calendar.HOUR_OF_DAY, 21)
        windowEnd.set(Calendar.MINUTE, 0)

        setUpSpinners()
        setUpPickers()
        loadExistingReminder()
        setUpListeners()

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        if (appliedTheme != ThemePrefs.themeResId(this)) recreate()
    }

    private fun bindViews() {
        titleInput = findViewById(R.id.title_input)
        repeatSpinner = findViewById(R.id.repeat_spinner)
        dateTimeBlock = findViewById(R.id.date_time_block)
        dateTimeLabel = findViewById(R.id.date_time_label)
        dateButton = findViewById(R.id.date_button)
        timeButton = findViewById(R.id.time_button)
        timeBlock = findViewById(R.id.time_block)
        timeButtonOnly = findViewById(R.id.time_button_only)
        weeklyBlock = findViewById(R.id.weekly_block)
        weekDayChips = listOf(
            findViewById(R.id.week_day_0),
            findViewById(R.id.week_day_1),
            findViewById(R.id.week_day_2),
            findViewById(R.id.week_day_3),
            findViewById(R.id.week_day_4),
            findViewById(R.id.week_day_5),
            findViewById(R.id.week_day_6)
        )
        monthlyBlock = findViewById(R.id.monthly_block)
        monthDayPicker = findViewById(R.id.month_day_picker)
        intervalBlock = findViewById(R.id.interval_block)
        intervalPicker = findViewById(R.id.interval_picker)
        intervalUnitSpinner = findViewById(R.id.interval_unit_spinner)
        windowSwitch = findViewById(R.id.window_switch)
        windowRow = findViewById(R.id.window_row)
        windowStartButton = findViewById(R.id.window_start_button)
        windowEndButton = findViewById(R.id.window_end_button)
        everyNBlock = findViewById(R.id.every_n_block)
        everyDaysPicker = findViewById(R.id.every_days_picker)
        note = findViewById(R.id.note)
        scheduleValue = findViewById(R.id.schedule_value)
        presetsBlock = findViewById(R.id.presets_block)
        presetButtons = listOf(
            findViewById(R.id.preset_15_min),
            findViewById(R.id.preset_1_hour),
            findViewById(R.id.preset_tomorrow)
        )
    }

    private fun setUpSpinners() {
        repeatSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.repeat_types)
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        intervalUnitSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.interval_units)
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun setUpPickers() {
        monthDayPicker.minValue = 1
        monthDayPicker.maxValue = 31
        monthDayPicker.value = 1
        monthDayPicker.wrapSelectorWheel = false

        everyDaysPicker.minValue = 1
        everyDaysPicker.maxValue = 90
        everyDaysPicker.value = 3
        everyDaysPicker.wrapSelectorWheel = false

        intervalPicker.minValue = 1
        intervalPicker.maxValue = MINUTES_MAX
        intervalPicker.value = 30
        intervalPicker.wrapSelectorWheel = false
    }

    private fun loadExistingReminder() {
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        if (id == 0L) return
        val reminder = ReminderStore.load(this).firstOrNull { it.id == id } ?: return
        existing = reminder
        titleInput.setText(reminder.title)
        lastType = reminder.repeatType
        repeatSpinner.setSelection(reminder.repeatType.ordinal)
        calendar.set(Calendar.HOUR_OF_DAY, reminder.hour)
        calendar.set(Calendar.MINUTE, reminder.minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (reminder.dateTimeMillis > 0) calendar.timeInMillis = reminder.dateTimeMillis
        weekDayChips.forEachIndexed { index, chip ->
            chip.isChecked = reminder.daysOfWeek.contains(Formats.calendarDayOfWeek(index))
        }
        monthDayPicker.value = reminder.dayOfMonth.coerceIn(1, 31)
        everyDaysPicker.value = reminder.everyNDays.coerceIn(1, 90)
        val wholeHours = reminder.intervalMinutes >= 60 && reminder.intervalMinutes % 60 == 0
        intervalUnitSpinner.setSelection(if (wholeHours) 1 else 0)
        applyIntervalRange(wholeHours)
        intervalPicker.value = if (wholeHours) {
            (reminder.intervalMinutes / 60).coerceIn(1, HOURS_MAX)
        } else {
            reminder.intervalMinutes.coerceIn(1, MINUTES_MAX)
        }
        windowSwitch.isChecked = reminder.windowEnabled
        windowStart.set(Calendar.HOUR_OF_DAY, reminder.windowStartMinutes / 60)
        windowStart.set(Calendar.MINUTE, reminder.windowStartMinutes % 60)
        windowEnd.set(Calendar.HOUR_OF_DAY, reminder.windowEndMinutes / 60)
        windowEnd.set(Calendar.MINUTE, reminder.windowEndMinutes % 60)
        findViewById<TextView>(R.id.screen_title).setText(R.string.edit_title)
    }

    private fun setUpListeners() {
        repeatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val type = RepeatType.entries.getOrElse(position) { RepeatType.ONCE }
                // Обработчик реагирует только на реальную смену типа: выбор пресета и
                // восстановление состояния не должны сбрасывать выбранный вариант.
                if (type == lastType) return
                lastType = type
                preset = Preset.NONE
                updateUi()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        intervalUnitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val hours = position == 1
                if (intervalPicker.maxValue != if (hours) HOURS_MAX else MINUTES_MAX) {
                    applyIntervalRange(hours)
                }
                updatePreview()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dateButton.setOnClickListener { pickDate() }
        timeButton.setOnClickListener { pickTime() }
        timeButtonOnly.setOnClickListener { pickTime() }
        windowStartButton.setOnClickListener { pickWindowTime(isStart = true) }
        windowEndButton.setOnClickListener { pickWindowTime(isStart = false) }

        weekDayChips.forEach { chip -> chip.setOnCheckedChangeListener { _, _ -> updatePreview() } }
        monthDayPicker.setOnValueChangedListener { _, _, _ -> updatePreview() }
        everyDaysPicker.setOnValueChangedListener { _, _, _ -> updatePreview() }
        intervalPicker.setOnValueChangedListener { _, _, _ -> updatePreview() }
        windowSwitch.setOnCheckedChangeListener { _, _ ->
            updateNote()
            windowRow.visibility = if (windowSwitch.isChecked) View.VISIBLE else View.GONE
            updatePreview()
        }

        presetButtons[0].setOnClickListener { applyPreset(Preset.IN_15_MIN, Formats.inMinutes(15)) }
        presetButtons[1].setOnClickListener { applyPreset(Preset.IN_1_HOUR, Formats.inMinutes(60)) }
        presetButtons[2].setOnClickListener { applyPreset(Preset.TOMORROW_MORNING, Formats.tomorrowAt(9, 0)) }

        findViewById<Button>(R.id.save_button).setOnClickListener { save() }
    }

    private fun applyIntervalRange(hours: Boolean) {
        intervalPicker.maxValue = if (hours) HOURS_MAX else MINUTES_MAX
        intervalPicker.value = intervalPicker.value.coerceIn(1, intervalPicker.maxValue)
    }

    private fun applyPreset(value: Preset, moment: Calendar) {
        calendar.timeInMillis = moment.timeInMillis
        // Пресет относится к однократному напоминанию.
        lastType = RepeatType.ONCE
        repeatSpinner.setSelection(RepeatType.ONCE.ordinal)
        preset = value
        updateUi()
    }

    private fun pickDate() {
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                preset = Preset.NONE
                updateUi()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = System.currentTimeMillis() - 60_000L
        dialog.show()
    }

    private fun pickTime() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                preset = Preset.NONE
                updateUi()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun pickWindowTime(isStart: Boolean) {
        val target = if (isStart) windowStart else windowEnd
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                target.set(Calendar.HOUR_OF_DAY, hourOfDay)
                target.set(Calendar.MINUTE, minute)
                updateUi()
            },
            target.get(Calendar.HOUR_OF_DAY),
            target.get(Calendar.MINUTE),
            true
        ).show()
    }

    private val repeatType: RepeatType
        get() = RepeatType.entries.getOrElse(repeatSpinner.selectedItemPosition) { RepeatType.ONCE }

    /** Собирает напоминание из текущего состояния формы. */
    private fun buildReminder(): Reminder {
        val type = repeatType
        val target = existing ?: Reminder(id = System.currentTimeMillis(), title = "")
        target.title = titleInput.text.toString().trim()
        target.repeatType = type
        target.hour = calendar.get(Calendar.HOUR_OF_DAY)
        target.minute = calendar.get(Calendar.MINUTE)
        target.dateTimeMillis = when (type) {
            RepeatType.ONCE, RepeatType.EVERY_N_DAYS -> calendar.timeInMillis
            else -> 0L
        }
        if (type == RepeatType.WEEKLY) {
            target.daysOfWeek = weekDayChips.mapIndexedNotNull { index, chip ->
                if (chip.isChecked) Formats.calendarDayOfWeek(index) else null
            }.toMutableSet()
        }
        target.dayOfMonth = monthDayPicker.value
        target.everyNDays = everyDaysPicker.value
        target.intervalMinutes = if (intervalUnitSpinner.selectedItemPosition == 1) {
            intervalPicker.value * 60
        } else {
            intervalPicker.value
        }
        target.windowEnabled = windowSwitch.isChecked
        target.windowStartMinutes = windowStart.get(Calendar.HOUR_OF_DAY) * 60 + windowStart.get(Calendar.MINUTE)
        target.windowEndMinutes = windowEnd.get(Calendar.HOUR_OF_DAY) * 60 + windowEnd.get(Calendar.MINUTE)
        return target
    }

    private fun updateUi() {
        val type = repeatType
        dateTimeBlock.visibility = if (type == RepeatType.ONCE || type == RepeatType.EVERY_N_DAYS) {
            View.VISIBLE
        } else {
            View.GONE
        }
        timeBlock.visibility = when (type) {
            RepeatType.DAILY, RepeatType.WEEKLY, RepeatType.MONTHLY -> View.VISIBLE
            else -> View.GONE
        }
        weeklyBlock.visibility = if (type == RepeatType.WEEKLY) View.VISIBLE else View.GONE
        monthlyBlock.visibility = if (type == RepeatType.MONTHLY) View.VISIBLE else View.GONE
        intervalBlock.visibility = if (type == RepeatType.INTERVAL) View.VISIBLE else View.GONE
        everyNBlock.visibility = if (type == RepeatType.EVERY_N_DAYS) View.VISIBLE else View.GONE
        presetsBlock.visibility = if (type == RepeatType.ONCE) View.VISIBLE else View.GONE
        dateTimeLabel.setText(
            if (type == RepeatType.EVERY_N_DAYS) R.string.start_label else R.string.date_time_label
        )
        windowRow.visibility = if (windowSwitch.isChecked) View.VISIBLE else View.GONE
        updateNote()
        updateLabels()
        updatePreview()
    }

    private fun updateNote() {
        note.visibility = if (repeatType == RepeatType.INTERVAL) View.VISIBLE else View.GONE
        note.setText(
            if (windowSwitch.isChecked) R.string.window_note_on else R.string.window_note_off
        )
    }

    private fun updateLabels() {
        dateButton.text = getString(R.string.date_button, Formats.day(calendar.timeInMillis))
        timeButton.text = getString(R.string.time_button, Formats.time(calendar.timeInMillis))
        timeButtonOnly.text = getString(R.string.time_button, Formats.time(calendar.timeInMillis))
        windowStartButton.text = getString(
            R.string.window_start_button,
            Formats.timeOfDay(windowStart.get(Calendar.HOUR_OF_DAY), windowStart.get(Calendar.MINUTE))
        )
        windowEndButton.text = getString(
            R.string.window_end_button,
            Formats.timeOfDay(windowEnd.get(Calendar.HOUR_OF_DAY), windowEnd.get(Calendar.MINUTE))
        )
        // Выбранный пресет выделяется фоном и рамкой, текст кнопки не меняется.
        presetButtons[0].isSelected = preset == Preset.IN_15_MIN
        presetButtons[1].isSelected = preset == Preset.IN_1_HOUR
        presetButtons[2].isSelected = preset == Preset.TOMORROW_MORNING
    }

    private fun updatePreview() {
        val reminder = buildReminder()
        val next = reminder.nextTriggerAt()
        scheduleValue.text = when {
            reminder.repeatType == RepeatType.WEEKLY && reminder.daysOfWeek.isEmpty() ->
                getString(R.string.preview_select_days)

            next == null && (reminder.repeatType == RepeatType.ONCE ||
                reminder.repeatType == RepeatType.EVERY_N_DAYS) -> getString(R.string.preview_past)

            next == null -> getString(R.string.preview_no_next)

            reminder.repeatType == RepeatType.ONCE ->
                getString(R.string.will_trigger, Formats.dateTime(next))

            else -> getString(R.string.next_trigger, Formats.dateTime(next))
        }
    }

    private fun validate(reminder: Reminder): String? = when {
        reminder.title.isEmpty() -> getString(R.string.error_empty_title)

        (reminder.repeatType == RepeatType.ONCE || reminder.repeatType == RepeatType.EVERY_N_DAYS) &&
            reminder.dateTimeMillis <= System.currentTimeMillis() -> getString(R.string.error_past_time)

        reminder.repeatType == RepeatType.WEEKLY && reminder.daysOfWeek.isEmpty() ->
            getString(R.string.error_week_days)

        reminder.repeatType == RepeatType.INTERVAL && reminder.windowEnabled &&
            reminder.windowEndMinutes <= reminder.windowStartMinutes -> getString(R.string.error_window)

        else -> null
    }

    private fun save() {
        val reminder = buildReminder()
        validate(reminder)?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        if (reminder.nextTriggerAt() == null) {
            Toast.makeText(this, R.string.error_schedule, Toast.LENGTH_LONG).show()
            return
        }

        val reminders = ReminderStore.load(this).filterNot { it.id == reminder.id }.toMutableList()
        reminders.add(reminder)
        if (!ReminderStore.save(this, reminders)) {
            // Не сообщаем об успехе, если запись не удалась.
            Toast.makeText(this, R.string.save_error, Toast.LENGTH_LONG).show()
            return
        }
        AlarmScheduler.schedule(this, reminder)
        finish()
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        private const val MINUTES_MAX = 180
        private const val HOURS_MAX = 24
    }
}
