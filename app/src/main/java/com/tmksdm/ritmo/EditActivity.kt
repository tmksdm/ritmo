package com.tmksdm.ritmo

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** Создание и редактирование напоминания. В Этапе 1 поддерживается однократное напоминание. */
class EditActivity : Activity() {

    private enum class Preset { NONE, IN_15_MIN, IN_1_HOUR, TOMORROW_MORNING }

    private lateinit var titleInput: EditText
    private lateinit var scheduleValue: TextView
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var preset15Min: Button
    private lateinit var preset1Hour: Button
    private lateinit var presetTomorrow: Button

    private val calendar: Calendar = Calendar.getInstance()
    private var preset = Preset.NONE
    private var existingId: Long = 0L
    private var editing = false
    private var appliedTheme = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = ThemePrefs.themeResId(this)
        setTheme(appliedTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        findViewById<View>(R.id.root).applySystemBarInsets()

        titleInput = findViewById(R.id.title_input)
        scheduleValue = findViewById(R.id.schedule_value)
        dateButton = findViewById(R.id.date_button)
        timeButton = findViewById(R.id.time_button)
        preset15Min = findViewById(R.id.preset_15_min)
        preset1Hour = findViewById(R.id.preset_1_hour)
        presetTomorrow = findViewById(R.id.preset_tomorrow)

        existingId = intent.getLongExtra(EXTRA_ID, 0L)
        if (existingId != 0L) {
            val existing = ReminderStore.load(this).firstOrNull { it.id == existingId }
            if (existing != null) {
                editing = true
                titleInput.setText(existing.title)
                calendar.timeInMillis = existing.dateTimeMillis
            }
        }
        findViewById<TextView>(R.id.screen_title)
            .setText(if (editing) R.string.edit_title else R.string.new_title)
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        preset15Min.setOnClickListener { applyPreset(Preset.IN_15_MIN, Formats.inMinutes(15)) }
        preset1Hour.setOnClickListener { applyPreset(Preset.IN_1_HOUR, Formats.inMinutes(60)) }
        presetTomorrow.setOnClickListener { applyPreset(Preset.TOMORROW_MORNING, Formats.tomorrowAt(9, 0)) }

        dateButton.setOnClickListener { pickDate() }
        timeButton.setOnClickListener { pickTime() }
        findViewById<Button>(R.id.save_button).setOnClickListener { save() }

        updateScheduleLabels()
    }

    override fun onResume() {
        super.onResume()
        if (appliedTheme != ThemePrefs.themeResId(this)) recreate()
    }

    private fun applyPreset(value: Preset, moment: Calendar) {
        calendar.timeInMillis = moment.timeInMillis
        preset = value
        updateScheduleLabels()
    }

    private fun pickDate() {
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                preset = Preset.NONE
                updateScheduleLabels()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // Прошедшие дни недоступны; сегодняшний день остаётся доступным.
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
                updateScheduleLabels()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateScheduleLabels() {
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        dateButton.text = getString(R.string.date_button, Formats.day(calendar.timeInMillis))
        timeButton.text = getString(R.string.time_button, Formats.time(calendar.timeInMillis))
        scheduleValue.text = getString(R.string.schedule_value, Formats.dateTime(calendar.timeInMillis))

        markPreset(preset15Min, R.string.preset_15_min, Preset.IN_15_MIN)
        markPreset(preset1Hour, R.string.preset_1_hour, Preset.IN_1_HOUR)
        markPreset(presetTomorrow, R.string.preset_tomorrow, Preset.TOMORROW_MORNING)
    }

    /** Выбранный пресет помечается галочкой, чтобы было видно, какое время подставлено. */
    private fun markPreset(button: Button, labelRes: Int, value: Preset) {
        val label = getString(labelRes)
        button.text = if (preset == value) getString(R.string.preset_selected, label) else label
    }

    private fun save() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_title, Toast.LENGTH_SHORT).show()
            titleInput.requestFocus()
            return
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.error_past_time, Toast.LENGTH_LONG).show()
            return
        }

        val reminders = ReminderStore.load(this)
        val reminder: Reminder
        if (editing) {
            reminder = reminders.firstOrNull { it.id == existingId } ?: return finish()
            reminder.title = title
            reminder.dateTimeMillis = calendar.timeInMillis
            reminder.enabled = true
        } else {
            reminder = Reminder(
                id = System.currentTimeMillis(),
                title = title,
                dateTimeMillis = calendar.timeInMillis
            )
            reminders.add(reminder)
        }
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
    }
}
