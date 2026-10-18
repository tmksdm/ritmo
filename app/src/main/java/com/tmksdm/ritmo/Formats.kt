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

    /** Полная дата со временем — для параметров расписания. */
    fun dateTime(millis: Long): String = fullDateTimeFormat.format(Date(millis))

    fun time(millis: Long): String = timeFormat.format(Date(millis))

    fun day(millis: Long): String = dayFormat.format(Date(millis))

    fun timeOfDay(minutesOfDay: Int): String =
        String.format(Locale.US, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60)

    /** Время суток для расписаний, где важны только часы и минуты. */
    fun timeOfDay(hour: Int, minute: Int): String = timeOfDay(hour * 60 + minute)

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
    fun scheduleDescription(context: Context, reminder: Reminder): String = when (reminder.repeatType) {
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

        RepeatType.DAILY -> context.getString(
            R.string.repeat_daily,
            timeOfDay(reminder.hour, reminder.minute)
        )

        RepeatType.WEEKLY -> context.getString(
            R.string.repeat_weekly_days,
            weekDaysLabel(context, reminder.daysOfWeek),
            timeOfDay(reminder.hour, reminder.minute)
        )

        RepeatType.MONTHLY -> context.getString(
            R.string.repeat_monthly_day,
            reminder.dayOfMonth,
            timeOfDay(reminder.hour, reminder.minute)
        )

        RepeatType.EVERY_N_DAYS -> context.getString(
            R.string.repeat_every_n_days_at,
            reminder.everyNDays,
            timeOfDay(reminder.hour, reminder.minute)
        )
    }

    /** Список выбранных дней недели: «Пн, Ср, Пт». */
    fun weekDaysLabel(context: Context, days: Set<Int>): String {
        val names = context.resources.getStringArray(R.array.week_days_short)
        return days.mapNotNull { calendarDay ->
            val index = weekDayIndex(calendarDay)
            if (index in names.indices) names[index] else null
        }.sortedBy { names.indexOf(it) }.joinToString(", ")
    }

    /** [Calendar.MONDAY] — 0, [Calendar.SUNDAY] — 6. */
    fun weekDayIndex(calendarDay: Int): Int = when (calendarDay) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }

    /** Значение [Calendar.DAY_OF_WEEK] по индексу списка дней недели. */
    fun calendarDayOfWeek(index: Int): Int = when (index) {
        0 -> Calendar.MONDAY
        1 -> Calendar.TUESDAY
        2 -> Calendar.WEDNESDAY
        3 -> Calendar.THURSDAY
        4 -> Calendar.FRIDAY
        5 -> Calendar.SATURDAY
        else -> Calendar.SUNDAY
    }

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
