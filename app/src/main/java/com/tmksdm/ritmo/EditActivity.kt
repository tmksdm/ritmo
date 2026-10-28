package com.tmksdm.ritmo

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** Создание и редактирование напоминания: все шесть видов расписания. */
class EditActivity : Activity() {

    private enum class Preset { NONE, IN_15_MIN, IN_1_HOUR, TOMORROW_MORNING }

    private lateinit var titleInput: EditText
    private lateinit var tagsContainer: LinearLayout
    private lateinit var tagsEmpty: TextView
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
    private lateinit var monthDayInput: EditText
    private lateinit var yearlyBlock: LinearLayout
    private lateinit var yearMonthSpinner: Spinner
    private lateinit var yearDayInput: EditText
    private lateinit var intervalBlock: LinearLayout
    private lateinit var intervalInput: EditText
    private lateinit var intervalUnitGroup: RadioGroup
    private lateinit var windowSwitch: Switch
    private lateinit var windowRow: LinearLayout
    private lateinit var windowStartButton: Button
    private lateinit var windowEndButton: Button
    private lateinit var everyNBlock: LinearLayout
    private lateinit var everyDaysInput: EditText
    private lateinit var note: TextView
    private lateinit var scheduleValue: TextView
    private lateinit var presetsBlock: LinearLayout
    private lateinit var presetButtons: List<Button>
    private lateinit var saveButton: Button
    private lateinit var autosaveStatus: TextView

    private val calendar: Calendar = Calendar.getInstance()
    private val windowStart: Calendar = Calendar.getInstance()
    private val windowEnd: Calendar = Calendar.getInstance()

    private var existing: Reminder? = null
    private var lastType: RepeatType = RepeatType.ONCE
    private var preset = Preset.NONE
    private var appliedTheme = 0
    private var selectedTags: MutableList<String> = mutableListOf()

    private val autosaveHandler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = Runnable { autosaveNow() }
    private val clearStatusRunnable = Runnable { autosaveStatus.text = "" }
    private var autosaveReady = false
    private var lastSavedSnapshot: String? = null

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

        setUpRepeatSpinner()
        setUpNumericInputs()
        loadExistingReminder()
        setUpTagChips()
        setUpListeners()

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        updateUi()

        // В режиме создания напоминание добавляется явной кнопкой,
        // при редактировании изменения сохраняются автоматически.
        if (existing == null) {
            saveButton.setText(R.string.create)
        } else {
            saveButton.visibility = View.GONE
        }
        autosaveReady = true
    }

    override fun onResume() {
        super.onResume()
        if (appliedTheme != ThemePrefs.themeResId(this)) recreate()
    }

    override fun onPause() {
        super.onPause()
        // Не теряем последние правки, если пользователь уходит с экрана.
        if (existing != null) {
            autosaveHandler.removeCallbacks(autosaveRunnable)
            autosaveNow()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autosaveHandler.removeCallbacksAndMessages(null)
    }

    private fun bindViews() {
        titleInput = findViewById(R.id.title_input)
        tagsContainer = findViewById(R.id.tags_container)
        tagsEmpty = findViewById(R.id.tags_empty)
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
        monthDayInput = findViewById(R.id.month_day_input)
        yearlyBlock = findViewById(R.id.yearly_block)
        yearMonthSpinner = findViewById(R.id.year_month_spinner)
        yearDayInput = findViewById(R.id.year_day_input)
        intervalBlock = findViewById(R.id.interval_block)
        intervalInput = findViewById(R.id.interval_input)
        intervalUnitGroup = findViewById(R.id.interval_unit_group)
        windowSwitch = findViewById(R.id.window_switch)
        windowRow = findViewById(R.id.window_row)
        windowStartButton = findViewById(R.id.window_start_button)
        windowEndButton = findViewById(R.id.window_end_button)
        everyNBlock = findViewById(R.id.every_n_block)
        everyDaysInput = findViewById(R.id.every_days_input)
        note = findViewById(R.id.note)
        scheduleValue = findViewById(R.id.schedule_value)
        presetsBlock = findViewById(R.id.presets_block)
        presetButtons = listOf(
            findViewById(R.id.preset_15_min),
            findViewById(R.id.preset_1_hour),
            findViewById(R.id.preset_tomorrow)
        )
        saveButton = findViewById(R.id.save_button)
        autosaveStatus = findViewById(R.id.autosave_status)
    }

    private fun setUpRepeatSpinner() {
        repeatSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.repeat_types)
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        yearMonthSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.months_genitive)
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun setUpNumericInputs() {
        listOf(monthDayInput, yearDayInput, everyDaysInput, intervalInput).forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    updatePreview()
                    scheduleAutosave()
                }
            })
        }
        clampOnBlur(monthDayInput, 1, MONTH_DAY_MAX)
        clampOnBlur(yearDayInput, 1, MONTH_DAY_MAX)
        clampOnBlur(everyDaysInput, 1, EVERY_DAYS_MAX)
        intervalInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) clampIntervalInput() }
        intervalUnitGroup.setOnCheckedChangeListener { _, _ ->
            clampIntervalInput()
            updatePreview()
            scheduleAutosave(0L)
        }
        // Название сохраняется после короткой паузы в вводе.
        titleInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = scheduleAutosave()
        })
    }

    /** Значения вне диапазона мягко подтягиваются к допустимым, чтобы не мешать вводу. */
    private fun clampOnBlur(input: EditText, min: Int, max: Int) {
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val value = numberOrNull(input) ?: return@setOnFocusChangeListener
            val clamped = value.coerceIn(min, max)
            if (clamped != value) input.setText(clamped.toString())
            scheduleAutosave(0L)
        }
    }

    private fun clampIntervalInput() {
        val value = numberOrNull(intervalInput) ?: return
        val clamped = value.coerceIn(1, intervalMax())
        if (clamped != value) intervalInput.setText(clamped.toString())
    }

    private val hoursSelected: Boolean
        get() = intervalUnitGroup.checkedRadioButtonId == R.id.interval_unit_hours

    private fun intervalMax(): Int = if (hoursSelected) HOURS_MAX else MINUTES_MAX

    private fun numberOrNull(input: EditText): Int? = input.text.toString().trim().toIntOrNull()

    private fun numberOrDefault(input: EditText, min: Int, max: Int, fallback: Int): Int =
        numberOrNull(input)?.coerceIn(min, max) ?: fallback

    private fun loadExistingReminder() {
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        if (id == 0L) {
            monthDayInput.setText(DEFAULT_MONTH_DAY.toString())
            yearMonthSpinner.setSelection(calendar.get(Calendar.MONTH))
            yearDayInput.setText(calendar.get(Calendar.DAY_OF_MONTH).toString())
            everyDaysInput.setText(DEFAULT_EVERY_DAYS.toString())
            intervalInput.setText(DEFAULT_INTERVAL_MINUTES.toString())
            return
        }
        val reminder = ReminderStore.load(this).firstOrNull { it.id == id } ?: return
        existing = reminder
        selectedTags = TagNames.normalizeAll(reminder.tags)
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
        monthDayInput.setText(reminder.dayOfMonth.coerceIn(1, MONTH_DAY_MAX).toString())
        yearMonthSpinner.setSelection((reminder.monthOfYear - 1).coerceIn(0, 11))
        yearDayInput.setText(reminder.dayOfMonth.coerceIn(1, MONTH_DAY_MAX).toString())
        everyDaysInput.setText(reminder.everyNDays.coerceIn(1, EVERY_DAYS_MAX).toString())
        val wholeHours = reminder.intervalMinutes >= 60 && reminder.intervalMinutes % 60 == 0
        intervalUnitGroup.check(if (wholeHours) R.id.interval_unit_hours else R.id.interval_unit_minutes)
        intervalInput.setText(
            if (wholeHours) {
                (reminder.intervalMinutes / 60).coerceIn(1, HOURS_MAX).toString()
            } else {
                reminder.intervalMinutes.coerceIn(1, MINUTES_MAX).toString()
            }
        )
        windowSwitch.isChecked = reminder.windowEnabled
        windowStart.set(Calendar.HOUR_OF_DAY, reminder.windowStartMinutes / 60)
        windowStart.set(Calendar.MINUTE, reminder.windowStartMinutes % 60)
        windowEnd.set(Calendar.HOUR_OF_DAY, reminder.windowEndMinutes / 60)
        windowEnd.set(Calendar.MINUTE, reminder.windowEndMinutes % 60)
        findViewById<TextView>(R.id.screen_title).setText(R.string.edit_title)
        // Отправная точка для автосохранения: без изменений запись не перезаписывается.
        lastSavedSnapshot = reminder.toJson().toString()
    }

    /**
     * Чипы существующих тегов. Отмеченные входят в selectedTags и сохраняются
     * вместе с напоминанием; определение тега создаётся на главном экране.
     */
    private fun setUpTagChips() {
        tagsContainer.removeAllViews()
        val available = TagNames.merge(
            Tags.load(this),
            selectedTags
        )
        tagsEmpty.visibility = if (available.isEmpty()) View.VISIBLE else View.GONE
        available.forEach { tag ->
            val chip = CheckBox(this).apply {
                text = tag
                isChecked = TagNames.contains(selectedTags, tag)
                setButtonDrawable(null)
                background = getDrawable(R.drawable.segment_background)
                setTextColor(resources.getColorStateList(R.color.chip_text, theme))
                gravity = Gravity.CENTER
                minHeight = dp(32)
                setPadding(dp(10), 0, dp(10), 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    TagNames.add(selectedTags, tag)
                } else {
                    selectedTags.removeAll { TagNames.same(it, tag) }
                }
                scheduleAutosave(0L)
            }
            tagsContainer.addView(chip)
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

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
                scheduleAutosave(0L)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dateButton.setOnClickListener { pickDate() }
        timeButton.setOnClickListener { pickTime() }
        timeButtonOnly.setOnClickListener { pickTime() }
        windowStartButton.setOnClickListener { pickWindowTime(isStart = true) }
        windowEndButton.setOnClickListener { pickWindowTime(isStart = false) }

        weekDayChips.forEach { chip ->
            chip.setOnCheckedChangeListener { _, _ ->
                updatePreview()
                scheduleAutosave(0L)
            }
        }
        yearMonthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePreview()
                scheduleAutosave(0L)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        windowSwitch.setOnCheckedChangeListener { _, _ ->
            updateNote()
            windowRow.visibility = if (windowSwitch.isChecked) View.VISIBLE else View.GONE
            updatePreview()
            scheduleAutosave(0L)
        }

        presetButtons[0].setOnClickListener { applyPreset(Preset.IN_15_MIN, Formats.inMinutes(15)) }
        presetButtons[1].setOnClickListener { applyPreset(Preset.IN_1_HOUR, Formats.inMinutes(60)) }
        presetButtons[2].setOnClickListener { applyPreset(Preset.TOMORROW_MORNING, Formats.tomorrowAt(9, 0)) }

        findViewById<Button>(R.id.save_button).setOnClickListener { save() }
    }

    /**
     * Автосохранение доступно только при редактировании и только после загрузки формы.
     * Выбор значения сохраняется сразу, ввод текста и чисел — после короткой паузы.
     */
    private fun scheduleAutosave(delayMs: Long = AUTOSAVE_DELAY_MS) {
        if (existing == null || !autosaveReady) return
        autosaveHandler.removeCallbacks(autosaveRunnable)
        autosaveHandler.postDelayed(autosaveRunnable, delayMs)
    }

    private fun autosaveNow() {
        val reminder = existing ?: return
        if (!autosaveReady) return
        buildReminder()
        // Сохраняется только полностью корректное состояние расписания.
        if (validate(reminder, requireFuture = false) != null) return
        val next = reminder.nextTriggerAt()
        if (next != null) reminder.reactivateIfRescheduled(next)
        val snapshot = reminder.toJson().toString()
        if (snapshot == lastSavedSnapshot) return
        if (persist(reminder)) {
            lastSavedSnapshot = snapshot
            showSavedStatus()
        }
    }

    private fun showSavedStatus() {
        autosaveStatus.setText(R.string.saved)
        autosaveHandler.removeCallbacks(clearStatusRunnable)
        autosaveHandler.postDelayed(clearStatusRunnable, SAVED_STATUS_MS)
    }

    /** Записывает напоминание и перепланирует уведомление. При ошибке сообщает об этом. */
    private fun persist(reminder: Reminder): Boolean {
        val reminders = ReminderStore.load(this).filterNot { it.id == reminder.id }.toMutableList()
        reminders.add(reminder)
        if (!ReminderStore.save(this, reminders)) {
            Toast.makeText(this, R.string.save_error, Toast.LENGTH_LONG).show()
            return false
        }
        AlarmScheduler.schedule(this, reminder)
        return true
    }

    private fun applyPreset(value: Preset, moment: Calendar) {
        calendar.timeInMillis = moment.timeInMillis
        // Пресет относится к однократному напоминанию.
        lastType = RepeatType.ONCE
        repeatSpinner.setSelection(RepeatType.ONCE.ordinal)
        preset = value
        updateUi()
        scheduleAutosave(0L)
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
                scheduleAutosave(0L)
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
                scheduleAutosave(0L)
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
                scheduleAutosave(0L)
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
        if (type == RepeatType.YEARLY) target.monthOfYear = yearMonthSpinner.selectedItemPosition + 1
        if (type == RepeatType.WEEKLY) {
            target.daysOfWeek = weekDayChips.mapIndexedNotNull { index, chip ->
                if (chip.isChecked) Formats.calendarDayOfWeek(index) else null
            }.toMutableSet()
        }
        target.dayOfMonth = if (type == RepeatType.YEARLY) {
            numberOrDefault(yearDayInput, 1, MONTH_DAY_MAX, DEFAULT_MONTH_DAY)
        } else {
            numberOrDefault(monthDayInput, 1, MONTH_DAY_MAX, DEFAULT_MONTH_DAY)
        }
        target.everyNDays = numberOrDefault(everyDaysInput, 1, EVERY_DAYS_MAX, DEFAULT_EVERY_DAYS)
        target.intervalMinutes = if (hoursSelected) {
            numberOrDefault(intervalInput, 1, HOURS_MAX, 1) * 60
        } else {
            numberOrDefault(intervalInput, 1, MINUTES_MAX, DEFAULT_INTERVAL_MINUTES)
        }
        target.windowEnabled = windowSwitch.isChecked
        target.windowStartMinutes = windowStart.get(Calendar.HOUR_OF_DAY) * 60 + windowStart.get(Calendar.MINUTE)
        target.windowEndMinutes = windowEnd.get(Calendar.HOUR_OF_DAY) * 60 + windowEnd.get(Calendar.MINUTE)
        target.tags = TagNames.normalizeAll(selectedTags)
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
            RepeatType.DAILY, RepeatType.WEEKLY, RepeatType.MONTHLY, RepeatType.YEARLY -> View.VISIBLE
            else -> View.GONE
        }
        weeklyBlock.visibility = if (type == RepeatType.WEEKLY) View.VISIBLE else View.GONE
        monthlyBlock.visibility = if (type == RepeatType.MONTHLY) View.VISIBLE else View.GONE
        yearlyBlock.visibility = if (type == RepeatType.YEARLY) View.VISIBLE else View.GONE
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

    private fun validate(reminder: Reminder, requireFuture: Boolean): String? {
        val intervalLimit = intervalMax()
        val now = System.currentTimeMillis()
        return when {
            reminder.title.isEmpty() -> getString(R.string.error_empty_title)

            reminder.repeatType == RepeatType.INTERVAL -> when {
                numberOrNull(intervalInput)?.let { it in 1..intervalLimit } != true ->
                    getString(R.string.error_number_range, 1, intervalLimit)

                reminder.windowEnabled && reminder.windowEndMinutes <= reminder.windowStartMinutes ->
                    getString(R.string.error_window)

                else -> null
            }

            reminder.repeatType == RepeatType.MONTHLY ||
                reminder.repeatType == RepeatType.YEARLY ->
                if (numberOrNull(if (reminder.repeatType == RepeatType.YEARLY) yearDayInput else monthDayInput)
                        ?.let { it in 1..MONTH_DAY_MAX } == true
                ) {
                    null
                } else {
                    getString(R.string.error_number_range, 1, MONTH_DAY_MAX)
                }

            reminder.repeatType == RepeatType.EVERY_N_DAYS -> when {
                numberOrNull(everyDaysInput)?.let { it in 1..EVERY_DAYS_MAX } != true ->
                    getString(R.string.error_number_range, 1, EVERY_DAYS_MAX)

                requireFuture && reminder.dateTimeMillis <= now -> getString(R.string.error_past_time)

                else -> null
            }

            reminder.repeatType == RepeatType.ONCE && requireFuture && reminder.dateTimeMillis <= now ->
                getString(R.string.error_past_time)

            reminder.repeatType == RepeatType.WEEKLY && reminder.daysOfWeek.isEmpty() ->
                getString(R.string.error_week_days)

            else -> null
        }
    }

    /** Явное создание нового напоминания: неполные записи в список не попадают. */
    private fun save() {
        val reminder = buildReminder()
        validate(reminder, requireFuture = true)?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        val next = reminder.nextTriggerAt()
        if (next == null) {
            Toast.makeText(this, R.string.error_schedule, Toast.LENGTH_LONG).show()
            return
        }
        reminder.reactivateIfRescheduled(next)
        if (persist(reminder)) finish()
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        private const val AUTOSAVE_DELAY_MS = 700L
        private const val SAVED_STATUS_MS = 2_000L
        private const val MINUTES_MAX = 180
        private const val HOURS_MAX = 24
        private const val MONTH_DAY_MAX = 31
        private const val EVERY_DAYS_MAX = 90
        private const val DEFAULT_INTERVAL_MINUTES = 30
        private const val DEFAULT_MONTH_DAY = 1
        private const val DEFAULT_EVERY_DAYS = 3
    }
}
