package com.tmksdm.ritmo

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Форматирование дат и описаний расписания для интерфейса. */
object Formats {

    private val dateTimeFormat = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("ru"))
    private val dayFormat = SimpleDateFormat("d MMMM", Locale("ru"))

    fun dateTime(millis: Long): String = dateTimeFormat.format(Date(millis))

    fun time(millis: Long): String = timeFormat.format(Date(millis))

    fun day(millis: Long): String = dayFormat.format(Date(millis))

    /** Текст под названием напоминания в списке. */
    fun subtitle(reminder: Reminder): String {
        val schedule = describeSchedule(reminder)
        if (!reminder.enabled) return "$schedule · выключено"
        val next = reminder.nextTriggerAt()
        return if (next != null) "$schedule · затем ${dateTime(next)}" else "$schedule · время уже прошло"
    }

    fun describeSchedule(reminder: Reminder): String = when (reminder.repeatType) {
        RepeatType.ONCE -> "Однократно, ${dateTime(reminder.dateTimeMillis)}"
        else -> "Повторение настраивается в следующем обновлении"
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
