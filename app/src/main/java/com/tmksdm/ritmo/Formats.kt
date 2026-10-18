package com.tmksdm.ritmo

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Форматирование дат и описаний расписания для интерфейса. */
object Formats {

    private val fullDateTimeFormat = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))
    private val shortDateTimeFormat = SimpleDateFormat("d MMMM, HH:mm", Locale("ru"))
    private val shortDateTimeWithYearFormat = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("ru"))
    private val dayFormat = SimpleDateFormat("d MMMM", Locale("ru"))

    /** Полная дата со временем — для поля «Сработает». */
    fun dateTime(millis: Long): String = fullDateTimeFormat.format(Date(millis))

    fun time(millis: Long): String = timeFormat.format(Date(millis))

    fun day(millis: Long): String = dayFormat.format(Date(millis))

    /**
     * Компактная дата для списка: год показывается только если он отличается от текущего,
     * чтобы не дублировать одинаковые сведения.
     */
    fun compactDateTime(millis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        return (if (sameYear) shortDateTimeFormat else shortDateTimeWithYearFormat).format(Date(millis))
    }

    /** Строка под названием напоминания в списке. */
    fun subtitle(context: Context, reminder: Reminder): String = when {
        reminder.repeatType == RepeatType.ONCE && reminder.lastTriggeredAt > 0 ->
            context.getString(R.string.schedule_done, compactDateTime(reminder.dateTimeMillis))

        !reminder.enabled -> context.getString(
            R.string.schedule_off,
            if (reminder.repeatType == RepeatType.ONCE) {
                compactDateTime(reminder.dateTimeMillis)
            } else {
                scheduleDescription(context, reminder)
            }
        )

        reminder.repeatType == RepeatType.ONCE ->
            context.getString(R.string.schedule_once, compactDateTime(reminder.dateTimeMillis))

        else -> {
            val description = scheduleDescription(context, reminder)
            val next = reminder.nextTriggerAt()
            if (next == null) {
                context.getString(R.string.schedule_expired, description)
            } else {
                context.getString(R.string.schedule_next, description, compactDateTime(next))
            }
        }
    }

    /** Краткое описание повторения без времени ближайшего срабатывания. */
    private fun scheduleDescription(context: Context, reminder: Reminder): String = when (reminder.repeatType) {
        RepeatType.ONCE -> context.getString(R.string.repeat_once_short)

        RepeatType.INTERVAL -> {
            val every = if (reminder.intervalMinutes >= 60 && reminder.intervalMinutes % 60 == 0) {
                context.getString(R.string.repeat_every_hours, reminder.intervalMinutes / 60)
            } else {
                context.getString(R.string.repeat_every_minutes, reminder.intervalMinutes)
            }
            if (reminder.windowEnabled) {
                context.getString(
                    R.string.repeat_interval_window,
                    every,
                    timeOfDay(reminder.windowStartMinutes),
                    timeOfDay(reminder.windowEndMinutes)
                )
            } else {
                every
            }
        }

        RepeatType.DAILY -> context.getString(R.string.repeat_daily, timeOfDay(reminder.hour * 60 + reminder.minute))
        RepeatType.WEEKLY -> context.getString(R.string.repeat_weekly)
        RepeatType.MONTHLY -> context.getString(R.string.repeat_monthly, reminder.dayOfMonth)
        RepeatType.EVERY_N_DAYS -> context.getString(R.string.repeat_every_n_days, reminder.everyNDays)
    }

    private fun timeOfDay(minutesOfDay: Int): String =
        String.format(Locale.US, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60)

    /** Полночь «завтрашнего дня» + указанный час/минута. */
    fun tomorrowAt(hour: Int, minute: Int): Calendar {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
    }

    fun inMinutes(minutes: Int): Calendar {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, minutes)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
    }
}
