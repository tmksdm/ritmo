package com.tmksdm.ritmo

import java.util.Calendar

/**
 * Расчёт следующего срабатывания. Все расписания привязаны к календарю, а не к моменту
 * открытия приложения или факту предыдущего уведомления, поэтому после перезагрузки,
 * смены времени или часового пояса расписание восстанавливается корректно.
 */
object Schedules {

    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun nextTrigger(reminder: Reminder, now: Long): Long? = when (reminder.repeatType) {
        RepeatType.ONCE -> reminder.dateTimeMillis.takeIf { it > now }
        RepeatType.INTERVAL -> nextInterval(reminder, now)
        RepeatType.DAILY -> nextDaily(reminder, now)
        RepeatType.WEEKLY -> nextWeekly(reminder, now)
        RepeatType.MONTHLY -> nextMonthly(reminder, now)
        RepeatType.EVERY_N_DAYS -> nextEveryNDays(reminder, now)
        RepeatType.YEARLY -> nextYearly(reminder, now)
    }

    /** Каждые N минут или часов. Без окна — круглосуточно, с окном — только внутри него. */
    fun nextInterval(reminder: Reminder, now: Long): Long? {
        val step = reminder.intervalMinutes.coerceAtLeast(1) * MINUTE_MS
        if (!reminder.windowEnabled) {
            // Отсчёт идёт от полуночи текущих суток: расписание предсказуемо и повторяемо.
            val midnight = startOfDay(now)
            return midnight + ((now - midnight) / step + 1) * step
        }
        val windowStartMinutes = reminder.windowStartMinutes.coerceIn(0, 1439)
        val windowEndMinutes = reminder.windowEndMinutes.coerceIn(0, 1439)
        if (windowEndMinutes <= windowStartMinutes) return null

        var dayStart = startOfDay(now)
        repeat(4) {
            val windowStart = dayStart + windowStartMinutes * MINUTE_MS
            val windowEnd = dayStart + windowEndMinutes * MINUTE_MS
            val candidate = if (windowStart > now) {
                windowStart
            } else {
                // Интервал отсчитывается от начала окна; момент окончания окна допустим,
                // если он точно попадает на шаг интервала.
                windowStart + ((now - windowStart) / step + 1) * step
            }
            if (candidate <= windowEnd) return candidate
            dayStart = startOfDay(nextDay(dayStart))
        }
        return null
    }

    fun nextDaily(reminder: Reminder, now: Long): Long? {
        val candidate = atTime(now, reminder.hour, reminder.minute)
        return if (candidate > now) candidate else atTime(nextDay(now), reminder.hour, reminder.minute)
    }

    fun nextWeekly(reminder: Reminder, now: Long): Long? {
        if (reminder.daysOfWeek.isEmpty()) return null
        var day = startOfDay(now)
        repeat(8) {
            val candidate = atTime(day, reminder.hour, reminder.minute)
            if (candidate > now && reminder.daysOfWeek.contains(dayOfWeek(day))) return candidate
            day = nextDay(day)
        }
        return null
    }

    /**
     * Ежемесячно в выбранный день. Если в месяце нет такого числа, используется
     * последний день месяца.
     */
    fun nextMonthly(reminder: Reminder, now: Long): Long? {
        val cursor = Calendar.getInstance().apply { timeInMillis = now }
        val year = cursor.get(Calendar.YEAR)
        val month = cursor.get(Calendar.MONTH)
        repeat(25) { offset ->
            val candidate = Calendar.getInstance().apply {
                clear()
                set(year, month + offset, 1, reminder.hour, reminder.minute, 0)
            }
            val lastDay = candidate.getActualMaximum(Calendar.DAY_OF_MONTH)
            candidate.set(Calendar.DAY_OF_MONTH, reminder.dayOfMonth.coerceIn(1, lastDay))
            if (candidate.timeInMillis > now) return candidate.timeInMillis
        }
        return null
    }

    /**
     * Ежегодно в выбранные месяц и день. Если такого числа в месяце нет, используется
     * последний день месяца. 29 февраля срабатывает 28 февраля в невисокосный год
     * и снова 29 февраля в високосный.
     */
    fun nextYearly(reminder: Reminder, now: Long): Long? {
        val month = reminder.monthOfYear.coerceIn(1, 12) - 1
        val cursor = Calendar.getInstance().apply { timeInMillis = now }
        val year = cursor.get(Calendar.YEAR)
        repeat(3) { offset ->
            val candidate = Calendar.getInstance().apply {
                clear()
                set(year + offset, month, 1, reminder.hour, reminder.minute, 0)
            }
            val lastDay = candidate.getActualMaximum(Calendar.DAY_OF_MONTH)
            candidate.set(Calendar.DAY_OF_MONTH, reminder.dayOfMonth.coerceIn(1, lastDay))
            if (candidate.timeInMillis > now) return candidate.timeInMillis
        }
        return null
    }

    /** Каждые N дней в установленное время, начиная с даты начала. */
    fun nextEveryNDays(reminder: Reminder, now: Long): Long? {
        val step = reminder.everyNDays.coerceAtLeast(1)
        val anchorMillis = if (reminder.dateTimeMillis > 0) reminder.dateTimeMillis else now
        val anchor = startOfDay(anchorMillis)
        val first = atTime(anchor, reminder.hour, reminder.minute)
        if (first > now) return first

        // Приблизительная оценка шага, затем уточнение: так не зависит от перехода часов.
        val daysSince = ((now - first) / DAY_MS).toInt().coerceAtLeast(0)
        var stepIndex = (daysSince / step).coerceAtLeast(0)
        repeat(8) {
            val candidate = atTime(addDays(anchor, stepIndex * step), reminder.hour, reminder.minute)
            if (candidate > now) return candidate
            stepIndex++
        }
        return null
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun nextDay(millis: Long): Long = addDays(millis, 1)

    private fun addDays(millis: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    private fun atTime(millis: Long, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayOfWeek(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
}
