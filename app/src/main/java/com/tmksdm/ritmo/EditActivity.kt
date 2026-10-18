package com.tmksdm.ritmo

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** Создание и редактирование напоминания. В Этапе 1 поддерживается однократное напоминание. */
class EditActivity : Activity() {

    private lateinit var titleInput: EditText
    private lateinit var scheduleButton: Button
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private val calendar: Calendar = Calendar.getInstance()
    private var existingId: Long = 0L
    private var editing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        titleInput = findViewById(R.id.title_input)
        scheduleButton = findViewById(R.id.schedule_value)
        dateButton = findViewById(R.id.date_button)
        timeButton = findViewById(R.id.time_button)

        existingId = intent.getLongExtra(EXTRA_ID, 0L)
        if (existingId != 0L) {
            val existing = ReminderStore.load(this).firstOrNull { it.id == existingId }
            if (existing != null) {
                editing = true
                titleInput.setText(existing.title)
                calendar.timeInMillis = existing.dateTimeMillis
            }
        }
        title = getString(if (editing) R.string.edit_title else R.string.new_title)

        findViewById<Button>(R.id.preset_15_min).setOnClickListener { applyPreset(Formats.inMinutes(15)) }
        findViewById<Button>(R.id.preset_1_hour).setOnClickListener { applyPreset(Formats.inMinutes(60)) }
        findViewById<Button>(R.id.preset_tomorrow).setOnClickListener { applyPreset(Formats.tomorrowAt(9, 0)) }

        dateButton.setOnClickListener { pickDate() }
        timeButton.setOnClickListener { pickTime() }
        findViewById<Button>(R.id.save_button).setOnClickListener { save() }

        updateScheduleLabels()
    }

    private fun applyPreset(value: Calendar) {
        calendar.timeInMillis = value.timeInMillis
        updateScheduleLabels()
    }

    private fun pickDate() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateScheduleLabels()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickTime() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
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
        scheduleButton.text = getString(R.string.schedule_value, Formats.dateTime(calendar.timeInMillis))
    }

    private fun save() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_title, Toast.LENGTH_SHORT).show()
            return
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.error_past_time, Toast.LENGTH_SHORT).show()
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
        ReminderStore.save(this, reminders)
        AlarmScheduler.schedule(this, reminder)
        finish()
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
    }
}
